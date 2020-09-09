package com.zhongyou.util.utils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.function.Consumer;
import com.zhongyou.util.function.Function;
import com.zhongyou.util.function.TriConsumer;
import com.zhongyou.util.ref.Ref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AtomicRequest<T, V> extends Request<T> {
	private final String TAG = AtomicRequest.class.getSimpleName();
	private Task mTimeoutCheckTask;

	private Request<T> mMajorAction;
	private Callback<T> mMajorActionCallback;
	private volatile Request<V> mRollbackAction;
	private volatile Callback<V> mRollbackActionCallback;
	private boolean mCancelingActionFlag;

	private Helper<V> mHelper;

	private final Callback<T> mMajorIntermediateCallback = new Callback<T>() {

		private void handelResult(RequestStatus result, Consumer<Callback<T>> callbackHandler) {
			synchronized (mRequestLock) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				setStatus(result);
				callbackHandler.accept(mMajorActionCallback);
			}
		}

		@Override
		public void accept(T ret) {
			handelResult(
					RequestStatus.Done,
					callback -> {
						setResultValue(ret);
						releaseResource("Major action accept result");
						try {
							callback.accept(ret);
						} catch (Exception e) {
							//ignore
						}
					}
			);
		}

		@Override
		public void onCanceled() {
			if (mCancelingActionFlag) {
				return;
			}
			Exception e = new RuntimeException("Inner action cancel abnormally");
			handelResult(
					RequestStatus.Exception,
					callback -> {
						Exception exp = new RuntimeException(String.format("Error executing atomic request tagged %s", getTag()), e);
						setResultException(exp);
						try {
							callback.onException(exp);
						} catch (Exception ee) {
							//ignore
						}
						try {
							ariseRollback();
						} catch (Exception ee) {
							ZyLogger.printException(TAG, new RuntimeException(String.format("Error arising rollback for atomic request tagged %s on major action cancel, calling chain: %s", getTag(), generateCallChainInfo()), ee));
						}
					}
			);
		}

		@Override
		public void onException(Exception e) {
			handelResult(
					RequestStatus.Exception,
					callback -> {
						Exception exp = new RuntimeException(String.format("Error executing atomic request tagged %s", getTag()), e);
						setResultException(exp);
						try {
							callback.onException(exp);
						} catch (Exception ee) {
							//ignore
						}
						try {
							ariseRollback();
						} catch (Exception ee) {
							ZyLogger.printException(TAG, new RuntimeException(String.format("Error arising rollback for atomic request tagged %s on major action exception, calling chain: %s", getTag(), generateCallChainInfo()), ee));
						}
					}
			);
		}

		@Override
		public void onTimeout() {
			handelResult(
					RequestStatus.Timeout,
					callback -> {
						try {
							callback.onTimeout();
						} catch (Exception e) {
							//ignore
						}
						try {
							ariseRollback();
						} catch (Exception e) {
							ZyLogger.printException(TAG, new RuntimeException(String.format("Error arising rollback for atomic request tagged %s on major action timeout, calling chain: %s", getTag(), generateCallChainInfo()), e));
						}
					}
			);
		}
	};

	private final Callback<V> mRollbackIntermediateCallback = new Callback<V>() {

		@Override
		public void accept(V ret) {
			synchronized (mRequestLock) {
				releaseResource("Rollback action on result");
				try {
					mRollbackActionCallback.accept(ret);
				} catch (Exception e) {
					//ignore
				}
			}
		}

		@Override
		public void onCanceled() {
			synchronized (mRequestLock) {
				releaseResource("Rollback action on cancel");
				try {
					mRollbackActionCallback.onCanceled();
				} catch (Exception e) {
					//ignore
				}
			}
		}

		@Override
		public void onException(Exception e) {
			synchronized (mRequestLock) {
				releaseResource(String.format("Rollback on exception: %s", e.getMessage()));
				try {
					mRollbackActionCallback.onException(new RuntimeException(String.format("Error executing rollback action for atomic request tagged %s", getTag()), e));
				} catch (Exception ee) {
					//ignore
				}
			}
		}

		@Override
		public void onTimeout() {
			synchronized (mRequestLock) {
				releaseResource("Rollback action timeout");
				try {
					mRollbackActionCallback.onTimeout();
				} catch (Exception e) {
					//ignore
				}
			}
		}
	};

	private void releaseResource(String message) {
		if (mHelper.isResourceAcquired()) {
			try {
				if (!mHelper.releaseResource()) {
					ZyLogger.e(TAG, String.format("Failed to release resource for atomic request tagged %s: %s, calling chain: %s", getTag(), message, generateCallChainInfo()));
				}
			} catch (Exception e) {
				ZyLogger.printException(TAG, new RuntimeException(String.format("Error releasing resource for atomic request tagged %s: %s, calling chain: %s", getTag(), message, generateCallChainInfo()), e));
			}
		}
	}

	public AtomicRequest() {

	}

	public AtomicRequest(Request<T> majorAction, Helper<V> helper) {
		mMajorAction = majorAction;
		mHelper = helper;
	}

	public void setMajorAction(Request<T> majorAction) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Try to set major action for a atomic request tagged %s which is not idle", getTag()));
			}
			mMajorAction = majorAction;
		}
	}

	public void setHelper(Helper<V> helper) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Try to set major action for a atomic request tagged %s  which is not idle", getTag()));
			}
			mHelper = helper;
		}
	}

	@Override
	public void launch(Callback<T> callback, long timeout) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Try to launch a atomic request tagged %s which is not idle", getTag()));
			}
			Objects.requireNonNull(callback);
			mMajorActionCallback = callback;
			setStatus(RequestStatus.Pending);
			timeout = Math.max(timeout, Request.TIMEOUT_UNLIMITED);
			setTimeout(timeout);
			setLaunchTime(System.currentTimeMillis());
			List<Request> callingChain = new ArrayList<>();
			callingChain.addAll(getCallingChain());
			callingChain.add(this);
			mMajorAction.setupCallingChain(callingChain);
			mMajorAction.setTaskScheduler(getTaskScheduler());
			boolean resAcquired = false;
			Exception expAcquiringRes = null;
			try {
				resAcquired = mHelper.acquireResource();
			} catch (Exception e) {
				expAcquiringRes = e;
			}
			if (!resAcquired) {
				String msg = String.format("Unable to acquire resource for atomic request tagged %s", getTag());
				Exception e = expAcquiringRes == null ? new RuntimeException(msg) : new RuntimeException(msg, expAcquiringRes);
				setResultException(e);
				setStatus(RequestStatus.Exception);
				try {
					callback.onException(e);
				} catch (Exception ee) {
					//ignore
				}
				return;
			}
			if (timeout > 0) {
				mTimeoutCheckTask = scheduleTaskDelayed(
						() -> {
							synchronized (mRequestLock) {
								if (!isRequestRunning()) {
									return;
								}
								setStatus(RequestStatus.Timeout);
								try {
									mMajorAction.cancel();
								} catch (Exception e) {
									ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling major action for atomic request tagged %s on reaching timeout, calling chain: %s", getTag(), generateCallChainInfo()), e));
								}
								try {
									ariseRollback();
								} catch (Exception ee) {
									ZyLogger.printException(TAG, new RuntimeException(String.format("Error arising rollback for atomic request tagged %s on reaching timeout, calling chain: %s", getTag(), generateCallChainInfo()), ee));
								}
								try {
									mMajorActionCallback.onTimeout();
								} catch (Exception e) {
									//ignore
								}
							}
						},
						timeout
				);
			}
			try {
				onLaunch();
				mMajorAction.launch(mMajorIntermediateCallback, timeout);
			} catch (Exception e) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				Exception exp = new RuntimeException(String.format("Failed to launch  atomic request tagged %s", getTag()), e);
				setResultException(exp);
				setStatus(RequestStatus.Exception);
				try {
					callback.onException(exp);
				} catch (Exception ee) {
					//ignore
				}
			}
		}
	}

	@Override
	public void cancel() {
		synchronized (mRequestLock) {
			if (!isRequestRunning()) {
				return;
			}
			if (mMajorAction.isRequestRunning()) {
				mCancelingActionFlag = true;
				try {
					mMajorAction.cancel();
				} catch (Exception e) {
					throw new RuntimeException(String.format("Error canceling major action for atomic action tagged %s on invoking cancel", getTag()), e);
				} finally {
					mCancelingActionFlag = false;
				}
			}
			if (!isRequestRunning()) {
				return;
			}
			Wnn.c(mTimeoutCheckTask, Task::cancel);
			setStatus(RequestStatus.Canceled);
			try {
				mMajorActionCallback.onCanceled();
			} catch (Exception e) {
				//ignore
			}
			try {
				ariseRollback();
			} catch (Exception ee) {
				ZyLogger.printException(TAG, new RuntimeException(String.format("Error arising rollback for atomic request tagged %s on invoking cancel, calling chain: %s", getTag(), generateCallChainInfo()), ee));
			}
		}
	}

	private void ariseRollback() {
		Ref<Request<V>> rollbackActionRef = new Ref<>();
		Ref<Callback<V>> rollbackCallbackRef = new Ref<>();
		Ref<Long> rollbackTimeoutRef = new Ref<>();
		try {
			mHelper.generateRollbackRequest(rollbackActionRef, rollbackCallbackRef, rollbackTimeoutRef);
			Objects.requireNonNull(rollbackActionRef.value);
			Objects.requireNonNull(rollbackCallbackRef.value);
			Objects.requireNonNull(rollbackTimeoutRef.value);
		} catch (Exception e) {
			releaseResource("arise rollback");
			throw new RuntimeException("Unable to generate roll back request");
		}
		mRollbackAction = rollbackActionRef.value;
		mRollbackActionCallback = rollbackCallbackRef.value;
		mRollbackAction.setTag(String.format("Rollback_for_%s", getTag()));
		List<Request> callingChain = new ArrayList<>();
		callingChain.addAll(getCallingChain());
		callingChain.add(this);
		mRollbackAction.setupCallingChain(callingChain);
		mRollbackAction.setTaskScheduler(getTaskScheduler());
		try {
			mRollbackAction.launch(mRollbackIntermediateCallback, rollbackTimeoutRef.value);
		} catch (Exception e) {
			try {
				mRollbackActionCallback.onException(new RuntimeException(String.format("Failed to launch roll back action for atomic request tagged %s", getTag()), e));
			} catch (Exception ee) {
				//ignore
			}
		}
	}


	public Request<V> getRollbackAction() {
		return mRollbackAction;
	}

	public Helper<V> getHelper() {
		return mHelper;
	}

	@Override
	protected String generateCallingInfo() {
		return String.format("(Atomic Request) %s", getTag());
	}

	@Override
	public String dumpCallingStatus() {
		synchronized (mRequestLock) {
			RequestStatus status = getStatus();
			String content = "";
			switch (status) {
				case Pending:
					content = String.format("Running: %sms remains", getTimeoutRemains());
					break;
				case Idle:
				case Timeout:
				case Canceled:
					content = status.name();
					break;
				case Done:
					content = String.format("Done: result {%s}", printResultValue());
					break;
				case Exception:
					content = String.format("Exception: %s", Wnn.f(getResultException(), Exception::getMessage));
					break;
			}
			return concatDumpTree(String.format("%s: %s, resource held: %s", generateCallingInfo(), content, mHelper.isResourceAcquired()), Arrays.asList((String) Wnn.f(mMajorAction, Request::dumpCallingStatus)));
		}
	}

	public interface Helper<T> {

		default T rollbackDefaultValue() {
			return null;
		}

		default void generateRollbackRequest(
				Ref<Request<T>> rollbackActionRef,
				Ref<Callback<T>> rollbackCallbackRef,
				Ref<Long> rollbackTimeoutRef
		) {
			rollbackActionRef.value = new SimpleRequest<>(new SimpleRequest.RequestAction<T>() {
				@Override
				public void execute(Callback<T> callback, long timeout) {
					callback.accept(rollbackDefaultValue());
				}

				@Override
				public void cancel() {

				}
			});
			rollbackActionRef.value.setTag("Roll back action for atomic action");
			rollbackCallbackRef.value = t -> {
				//do nothing
			};
			rollbackTimeoutRef.value = 0L;
		}

		boolean isResourceAcquired();

		boolean acquireResource();

		boolean releaseResource();

	}

	public static class ResourceModificationRequestManager<T> {
		private final Object mModificationLock = new Object();
		private final BiMap<T, AtomicRequest<?, ?>> mRequests = HashBiMap.create();
		private final String TAG;
		private final Function<T, String> mResourceInfoGenerator;
		private volatile boolean mIsEnabled = true;

		public ResourceModificationRequestManager(String tag, Function<T, String> resourceInfoGenerator) {
			TAG = tag;
			this.mResourceInfoGenerator = resourceInfoGenerator;
		}

		public boolean isEnabled() {
			return mIsEnabled;
		}

		public void setEnabled(boolean enabled) {
			synchronized (mModificationLock) {
				if (enabled == mIsEnabled) {
					return;
				}
				mIsEnabled = enabled;
				if (!enabled) {
					cancelAllRequests();
				}
			}
		}

		public void cancelAllRequests() {
			synchronized (mModificationLock) {
				for (Request request : mRequests.values()) {
					request.cancel();
				}
				mRequests.clear();
			}
		}

		public AtomicRequest.Helper<Boolean> generateModificationHelper(
				T resource,
				AtomicRequest<?, ?> request
		) {
			return new AtomicRequest.Helper<Boolean>() {

				@Override
				public Boolean rollbackDefaultValue() {
					return true;
				}

				@Override
				public boolean isResourceAcquired() {
					synchronized (mModificationLock) {
						return mRequests.get(resource) == request;
					}
				}

				@Override
				public boolean acquireResource() {
					if (!mIsEnabled) {
						return false;
					}
					synchronized (mModificationLock) {
						if (!mIsEnabled) {
							return false;
						}
						Request previous = mRequests.get(resource);
						if (previous != null) {
							if (previous == request) {
								return true;
							}
							ZyLogger.w(TAG, String.format("Trying to acquire resource %s for atomic request tagged %s when previous request tagged %s unfinished\n Calling chain: %s\n Calling chain for previous request: %s", mResourceInfoGenerator.apply(resource), request.getTag(), previous.getTag(), request.generateCallChainInfo(), previous.generateCallChainInfo()));
							return false;
						}
						mRequests.put(resource, request);
						return true;
					}
				}

				@Override
				public boolean releaseResource() {
					synchronized (mModificationLock) {
						Request previous = mRequests.get(resource);
						if (previous != null) {
							if (previous == request) {
								mRequests.remove(resource);
								return true;
							} else {
								ZyLogger.w(TAG, String.format("Trying to release resource %s for atomic request tagged %s when previous request tagged %s unfinished\n Calling chain: %s\n Calling chain for previous request: %s", mResourceInfoGenerator.apply(resource), request.getTag(), previous.getTag(), request.generateCallChainInfo(), previous.generateCallChainInfo()));
								return false;
							}
						} else {
							return false;
						}
					}
				}
			};
		}

		public AtomicRequest.Helper<Boolean> generateModificationHelper(
				T resource,
				AtomicRequest<?, ?> request,
				TriConsumer<Ref<Request<Boolean>>, Ref<Callback<Boolean>>, Ref<Long>> rollbackActionGenerator
		) {
			return new AtomicRequest.Helper<Boolean>() {

				@Override
				public void generateRollbackRequest(Ref<Request<Boolean>> rollbackActionRef, Ref<Callback<Boolean>> rollbackCallbackRef, Ref<Long> rollbackTimeoutRef) {
					rollbackActionGenerator.accept(rollbackActionRef, rollbackCallbackRef, rollbackTimeoutRef);
				}

				@Override
				public Boolean rollbackDefaultValue() {
					return true;
				}

				@Override
				public boolean isResourceAcquired() {
					synchronized (mModificationLock) {
						return mRequests.get(resource) == request;
					}
				}

				@Override
				public boolean acquireResource() {
					if (!mIsEnabled) {
						return false;
					}
					synchronized (mModificationLock) {
						if (!mIsEnabled) {
							return false;
						}
						Request previous = mRequests.get(resource);
						if (previous != null) {
							if (previous == request) {
								return true;
							}
							ZyLogger.w(TAG, String.format("Trying to acquire resource %s for atomic request tagged %s when previous request tagged %s unfinished\n Calling chain: %s\n Calling chain for previous request: %s", mResourceInfoGenerator.apply(resource), request.getTag(), previous.getTag(), request.generateCallChainInfo(), previous.generateCallChainInfo()));
							return false;
						}
						mRequests.put(resource, request);
						return true;
					}
				}

				@Override
				public boolean releaseResource() {
					synchronized (mModificationLock) {
						Request previous = mRequests.get(resource);
						if (previous != null) {
							if (previous == request) {
								mRequests.remove(resource);
								return true;
							} else {
								ZyLogger.w(TAG, String.format("Trying to release resource %s for atomic request tagged %s when previous request tagged %s unfinished\n Calling chain: %s\n Calling chain for previous request: %s", mResourceInfoGenerator.apply(resource), request.getTag(), previous.getTag(), request.generateCallChainInfo(), previous.generateCallChainInfo()));
								return false;
							}
						} else {
							return false;
						}
					}
				}
			};
		}
	}
}
