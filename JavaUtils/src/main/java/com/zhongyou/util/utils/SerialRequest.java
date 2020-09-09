package com.zhongyou.util.utils;

import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.ref.Ref;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class SerialRequest<T> extends CompositeRequest<T> {
	private static final String TAG = ParallelRequest.class.getSimpleName();
	private final List<Request<?>> mSubActions = new ArrayList<>();
	private final Stack<Request<?>> mSubActionExecutionStack = new Stack<>();
	private final Deque<Request<?>> mSubActionScheduleQueue = new ArrayDeque<>();
	private final ValueCompositor<T> mValueCompositor;
	private Callback<T> mCallback;
	private Task mTimeoutCheckTask;

	public SerialRequest(ValueCompositor<T> valueCompositor) {
		mValueCompositor = valueCompositor;
	}

	@Override
	public void setupSubActions(List<Request<?>> subActions) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				return;
			}
			if (!mSubActions.isEmpty()) {
				throw new RuntimeException("Error setting up sub actions: Sub action was already assigned");
			}
			if (subActions.isEmpty()) {
				throw new RuntimeException("Error setting up sub actions: No sub action offered");
			}
			mSubActions.addAll(subActions);
		}
	}

	@Override
	public void launch(Callback<T> callback, long timeout) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Launch a serial request tagged %s, which is in status %s", getTag(), getStatus()));
			}
			if (mSubActions.isEmpty()) {
				throw new RuntimeException(String.format("Launch a serial request tagged %s to which no sub action is offered", getTag()));
			}
			Objects.requireNonNull(callback);
			for (Request<?> subAction : mSubActions) {
				mSubActionScheduleQueue.offer(subAction);
			}
			mCallback = callback;
			setStatus(RequestStatus.Pending);
			timeout = Math.max(timeout, Request.TIMEOUT_UNLIMITED);
			setTimeout(timeout);
			setLaunchTime(System.currentTimeMillis());
			if (timeout > 0) {
				mTimeoutCheckTask = scheduleTaskDelayed(
						() -> {
							synchronized (mRequestLock) {
								if (!isRequestRunning()) {
									return;
								}
								setStatus(RequestStatus.Timeout);
								Request<?> subAction = mSubActionExecutionStack.peek();
								if (subAction != null && subAction.isRequestRunning()) {
									try {
										subAction.cancel();
									} catch (Exception e) {
										ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling sub action tagged %s for serial request tagged %s when reaching timeout, calling chain: %s", subAction.getTag(), getTag(), generateCallChainInfo()), e));
									}
								}
								try {
									mCallback.onTimeout();
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
				scheduleNextSubAction();
			} catch (Exception e) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				Exception exp = new RuntimeException(String.format("Failed to launch serial request tagged %s", getTag()), e);
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

	private void scheduleNextSubAction() {
		scheduleTaskDelayed(
				() -> {
					synchronized (mRequestLock) {
						if (!isRequestRunning()) {
							return;
						}
						try {
							pullNextSubActionAndExecute();
						} catch (Exception e) {
							Exception exp = new RuntimeException("Error pulling next sub action", e);
							if (!isRequestRunning()) {
								ZyLogger.printException(TAG, exp);
								return;
							}
							setResultException(exp);
							setStatus(RequestStatus.Exception);
							Wnn.c(mTimeoutCheckTask, Task::cancel);
							try {
								mCallback.onException(exp);
							} catch (Exception ee) {
								//ignore
							}
						}
					}
				},
				0
		);
	}

	private void pullNextSubActionAndExecute() {
		synchronized (mRequestLock) {
			if (!isRequestRunning()) {
				return;
			}
			if (mSubActionScheduleQueue.isEmpty()) {
				T composition = null;
				try {
					composition = mValueCompositor.composite(new ArrayList<>(mSubActions));
				} catch (Exception e) {
					if (!isRequestRunning()) {
						return;
					}
					Exception exp = new RuntimeException("Error composing result", e);
					setStatus(RequestStatus.Exception);
					setResultException(exp);
					Wnn.c(mTimeoutCheckTask, Task::cancel);
					try {
						mCallback.onException(exp);
					} catch (Exception eee) {
						//ignore
					}
					return;
				}
				setResultValue(composition);
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				setStatus(RequestStatus.Done);
				try {
					mCallback.accept(composition);
				} catch (Exception e) {
					//ignore
				}
				return;
			}
			long remainingTimeout = getTimeoutRemains();
			if (remainingTimeout <= 0 && getTimeout() > 0) {
				setStatus(RequestStatus.Timeout);
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				try {
					mCallback.onTimeout();
				} catch (Exception e) {
					//ignore
				}
				return;
			}
			Request subAction = mSubActionScheduleQueue.poll();
			mSubActionExecutionStack.push(subAction);
			List<Request> callingChain = new ArrayList<>();
			callingChain.addAll(getCallingChain());
			callingChain.add(this);
			subAction.setupCallingChain(callingChain);
			subAction.setTaskScheduler(getTaskScheduler());
			try {
				subAction.launch(
						new Callback<Object>() {

							private void handleResult() {
								synchronized (mRequestLock) {
									if (!isRequestRunning() || subAction.isRequestRunning()) {
										return;
									}
									if (subAction.getStatus() == RequestStatus.Canceled) {
										Exception e = new RuntimeException(String.format("Sub action tagged %s cancel abnormally", subAction.getTag()));
										setResultException(e);
										Wnn.c(mTimeoutCheckTask, Task::cancel);
										setStatus(RequestStatus.Exception);
										try {
											mCallback.onException(e);
										} catch (Exception ee) {
											//ignore
										}
										return;
									}
									Ref<Exception> errorMsg = new Ref<>();
									Exception exp;
									boolean proceed = false;
									try {
										proceed = mValueCompositor.testProceedAvailable(subAction, errorMsg);
										exp = errorMsg.value;
									} catch (Exception e) {
										exp = e;
									}
									if (proceed) {
										scheduleNextSubAction();
										return;
									}
									String msg = String.format("Unable to proceed sub action tagged %s in serial request tagged %s", subAction.getTag(), getTag());
									Exception exception = exp == null ? new RuntimeException(msg) : new RuntimeException(msg, exp);
									setResultException(exception);
									Wnn.c(mTimeoutCheckTask, Task::cancel);
									setStatus(RequestStatus.Exception);
									try {
										mCallback.onException(exception);
									} catch (Exception e) {
										//ignore
									}
									return;
								}
							}

							@Override
							public void accept(Object ret) {
								handleResult();
							}

							@Override
							public void onCanceled() {
								handleResult();
							}

							@Override
							public void onException(Exception e) {
								handleResult();
							}

							@Override
							public void onTimeout() {
								handleResult();
							}
						},
						remainingTimeout
				);
			} catch (Exception e) {
				synchronized (mRequestLock) {
					if (!isRequestRunning()) {
						//ignore
						return;
					}
					Wnn.c(mTimeoutCheckTask, Task::cancel);
					Exception exp = new RuntimeException(String.format("Error launching sub action tagged %s", subAction.getTag()), e);
					setResultException(exp);
					setStatus(RequestStatus.Exception);
					try {
						mCallback.onException(exp);
					} catch (Exception ee) {
						//ignore
					}
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
			Wnn.c(mTimeoutCheckTask, Task::cancel);
			setStatus(RequestStatus.Canceled);
			Request<?> subAction = mSubActionExecutionStack.peek();
			if (subAction != null && subAction.isRequestRunning()) {
				try {
					subAction.cancel();
				} catch (Exception e) {
					ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling sub action tagged %s for serial request tagged %s when invoking cancel, calling chain: %s", subAction.getTag(), getTag(), generateCallChainInfo()), e));
				}
			}
			try {
				mCallback.onCanceled();
			} catch (Exception e) {
				//ignore
			}
		}
	}

	@Override
	protected String generateCallingInfo() {
		return String.format("(Serial Request) %s", getTag());
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
			List<String> subActionStatus = new ArrayList<>();
			for (Request subAction : mSubActions) {
				subActionStatus.add(subAction.dumpCallingStatus());
			}
			return concatDumpTree(String.format("%s:: %s", generateCallingInfo(), content), subActionStatus);
		}
	}

	public interface ValueCompositor<T> {

		T composite(List<Request> subActions);

		default boolean testProceedAvailable(Request<?> request, Ref<Exception> errorMsg) {
			RequestStatus requestStatus = request.getStatus();
			switch (requestStatus) {
				case Done:
					return true;
				case Exception:
					errorMsg.value = request.getResultException();
					return false;
				case Timeout:
				case Canceled:
				case Idle:
				default:
					errorMsg.value = new RuntimeException(String.format("Unable to handle request in status %s, request tag: %s, calling chain: %s", requestStatus, request.getTag(), request.generateCallChainInfo()));
					return false;

			}
		}
	}
}
