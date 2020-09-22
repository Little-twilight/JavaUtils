package com.zhongyou.util.utils;

import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.function.Supplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LoopRequest<T> extends Request<T> {
	private final String TAG = LoopRequest.class.getSimpleName();
	private final List<Request<T>> mLoops = new ArrayList<>();
	private final Supplier<Request<T>> mLoopSupplier;
	private Task mTimeoutCheckTask;
	private Callback<T> mCallback;
	private boolean mCancelingActionFlag;
	private long mSingleLooperTimeout;
	private boolean mIgnoreException;

	private void doNextLoop() {
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
		Request<T> loop;
		try {
			loop = mLoopSupplier.get();
			if (loop == null) {
				setResultException(new RuntimeException("Failed to generate loop"));
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				setStatus(RequestStatus.Exception);
				try {
					mCallback.onException(getResultException());
				} catch (Exception ee) {
					//ignore
				}
				return;
			}
		} catch (Exception e) {
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
		loop.setTag(String.format("%s-loop-%s", getTag(), mLoops.size()));
		mLoops.add(loop);
		List<Request> callingChain = new ArrayList<>();
		callingChain.addAll(getCallingChain());
		callingChain.add(this);
		loop.setupCallingChain(callingChain);
		loop.setTaskScheduler(getTaskScheduler());
		try {
			loop.launch(
					new Callback<T>() {

						private void handleResult() {
							synchronized (mRequestLock) {
								if (!isRequestRunning() || loop.isRequestRunning()) {
									return;
								}
								if (loop.getStatus() == RequestStatus.Done) {
									T result = loop.getResultValue();
									setResultValue(result);
									Wnn.c(mTimeoutCheckTask, Task::cancel);
									setStatus(RequestStatus.Done);
									try {
										mCallback.accept(result);
									} catch (Exception ee) {
										//ignore
									}
									return;
								}
								if (loop.getStatus() == RequestStatus.Canceled) {
									Exception e = new RuntimeException(String.format("Loop tagged %s cancel abnormally", loop.getTag()));
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
								if (loop.getStatus() == RequestStatus.Exception) {
									if (mIgnoreException) {
										doNextLoop();
										return;
									}
									Exception e = new RuntimeException(String.format("Loop tagged %s encountered failure", loop.getTag()), loop.getResultException());
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
								if (loop.getStatus() == RequestStatus.Timeout) {
									doNextLoop();
								}
							}
						}

						@Override
						public void accept(T ret) {
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
					mSingleLooperTimeout
			);
		} catch (Exception e) {
			synchronized (mRequestLock) {
				if (!isRequestRunning()) {
					//ignore
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				Exception exp = new RuntimeException(String.format("Error launching loop tagged %s", loop.getTag()), e);
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

	public LoopRequest(Supplier<Request<T>> loopSupplier, long singleLooperTimeout) {
		this(loopSupplier, singleLooperTimeout, false);
	}

	public LoopRequest(Supplier<Request<T>> loopSupplier, long singleLooperTimeout, boolean ignoreException) {
		mLoopSupplier = loopSupplier;
		mSingleLooperTimeout = Math.max(singleLooperTimeout, Request.TIMEOUT_UNLIMITED);
		mIgnoreException = ignoreException;
	}

	@Override
	public void launch(Callback<T> callback, long timeout) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Launch a loop request tagged %s, which is in status %s", getTag(), getStatus()));
			}
			Objects.requireNonNull(callback);
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
								try {
									if (!mLoops.isEmpty()) {
										mLoops.get(mLoops.size() - 1).cancel();
									}
								} catch (Exception e) {
									ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling loop request tagged %s when reaching timeout, calling chain: %s", getTag(), generateCallChainInfo()), e));
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
				doNextLoop();
			} catch (Exception e) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				Exception exp = new RuntimeException(String.format("Error launching loop request tagged: %s", getTag()), e);
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
			mCancelingActionFlag = true;
			try {
				if (!mLoops.isEmpty()) {
					mLoops.get(mLoops.size() - 1).cancel();
				}
			} catch (Exception e) {
				throw new RuntimeException(String.format("Error canceling loop request tagged %s, calling chain: %s", getTag(), generateCallChainInfo()), e);
			} finally {
				mCancelingActionFlag = false;
			}
			if (!isRequestRunning()) {
				return;
			}
			Wnn.c(mTimeoutCheckTask, Task::cancel);
			setStatus(RequestStatus.Canceled);
			try {
				mCallback.onCanceled();
			} catch (Exception e) {
				//ignore
			}
		}
	}

	@Override
	protected String generateCallingInfo() {
		return String.format("(Loop Request) %s", getTag());
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
			for (Request subAction : mLoops) {
				subActionStatus.add(subAction.dumpCallingStatus());
			}
			return concatDumpTree(String.format("%s:: %s", generateCallingInfo(), content), subActionStatus);
		}
	}
}
