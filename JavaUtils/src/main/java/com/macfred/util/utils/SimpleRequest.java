package com.macfred.util.utils;

import com.macfred.util.Logger;
import com.macfred.util.function.Callback;
import com.macfred.util.function.Consumer;

import java.util.Objects;

public class SimpleRequest<T> extends Request<T> {
	private final String TAG = SimpleRequest.class.getSimpleName();
	private Task mTimeoutCheckTask;
	private Callback<T> mCallback;
	private RequestAction<T> mRequestAction;
	private boolean mCancelingActionFlag;

	private final Callback<T> mIntermediateCallback = new Callback<T>() {

		private void handelResult(RequestStatus result, Consumer<Callback<T>> callbackHandler) {
			synchronized (mRequestLock) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				setStatus(result);
				try {
					callbackHandler.accept(mCallback);
				} catch (Exception e) {
					//ignore
				}
			}
		}

		@Override
		public void accept(T ret) {
			handelResult(
					RequestStatus.Done,
					callback -> {
						setResultValue(ret);
						callback.accept(ret);
					}
			);
		}

		@Override
		public void onCanceled() {
			if (mCancelingActionFlag) {
				return;
			}
			Exception e = new RuntimeException("Inner action cancel abnormally");
			handelResult(RequestStatus.Exception,
					callback -> {
						setResultException(e);
						callback.onException(e);
					});
		}

		@Override
		public void onException(Exception e) {
			handelResult(RequestStatus.Exception,
					callback -> {
						setResultException(e);
						callback.onException(e);
					});
		}

		@Override
		public void onTimeout() {
			handelResult(RequestStatus.Timeout, Callback::onTimeout);
		}
	};

	public SimpleRequest(RequestAction<T> requestAction) {
		mRequestAction = requestAction;
	}

	@Override
	public void launch(Callback<T> callback, long timeout) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Launch a simple request tagged %s, which is in status %s", getTag(), getStatus()));
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
									mRequestAction.cancel();
								} catch (Exception e) {
									Logger.printException(TAG, new RuntimeException(String.format("Error canceling simple request tagged %s when reaching timeout, calling chain: %s", getTag(), generateCallChainInfo()), e));
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
				mRequestAction.execute(mIntermediateCallback, timeout);
			} catch (Exception e) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				Exception exp = new RuntimeException(String.format("Error launching simple request tagged: %s", getTag()), e);
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
				mRequestAction.cancel();
			} catch (Exception e) {
				throw new RuntimeException(String.format("Error canceling simple request tagged %s, calling chain: %s", getTag(), generateCallChainInfo()), e);
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
		return String.format("(Simple Request) %s", getTag());
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
			return String.format("%s:: %s", generateCallingInfo(), content);
		}
	}

	public interface RequestAction<T> {
		void execute(Callback<T> callback, long timeout);

		default void execute(Callback<T> callback) {
			execute(callback, 0);
		}

		void cancel();
	}
}
