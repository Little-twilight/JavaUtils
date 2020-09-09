package com.zhongyou.util.utils;

import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.collection.ArrayMap;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.ref.Ref;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ParallelRequest<T> extends CompositeRequest<T> {
	private static final String TAG = ParallelRequest.class.getSimpleName();
	private final Set<Request<?>> mSubActions = new HashSet<>();
	private final Map<Request<?>, RequestStatus> mSubActionResult = new ArrayMap<>();
	private final ValueCompositor<T> mValueCompositor;
	private Callback<T> mCallback;
	private Task mTimeoutCheckTask;

	public ParallelRequest(ValueCompositor<T> valueCompositor) {
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
				throw new RuntimeException(String.format("Launch a parallel request tagged %s, which is in status %s", getTag(), getStatus()));
			}
			if (mSubActions.isEmpty()) {
				throw new RuntimeException(String.format("Launch a parallel request tagged %s to which no sub action is offered", getTag()));
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
								for (Request<?> subAction : mSubActions) {
									if (subAction.isRequestRunning()) {
										try {
											subAction.cancel();
										} catch (Exception e) {
											ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling sub action tagged %s for parallel request tagged %s when reaching timeout, calling chain: %s", subAction.getTag(), getTag(), subAction.generateCallChainInfo()), e));
										}
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
				for (Request subAction : mSubActions) {
					List<Request> callingChain = new ArrayList<>();
					callingChain.addAll(getCallingChain());
					callingChain.add(this);
					subAction.setupCallingChain(callingChain);
					subAction.setTaskScheduler(getTaskScheduler());
					try {
						subAction.launch(
								new Callback<Object>() {

									private void handleResult(RequestStatus result) {
										synchronized (mRequestLock) {
											if (!isRequestRunning() || subAction.isRequestRunning()) {
												return;
											}
											mSubActionResult.put(subAction, result);
											Exception e;
											switch (result) {
												case Canceled:
													e = new RuntimeException("Inner action cancel abnormally");
													break;
												default:
													e = new RuntimeException(String.format("Inner action abnormally status: %s", result));
													break;
												case Timeout:
												case Exception:
												case Done:
												case Pending:
												case Idle:
													calc();
													return;
											}
											Exception exp = new RuntimeException(String.format("Error executing sub action tagged %s", subAction.getTag()), e);
											setStatus(RequestStatus.Exception);
											setResultException(exp);
											Wnn.c(mTimeoutCheckTask, Task::cancel);
											try {
												mCallback.onException(exp);
											} catch (Exception ee) {
												//ignore
											}
										}
									}

									@Override
									public void accept(Object ret) {
										handleResult(RequestStatus.Done);
									}

									@Override
									public void onCanceled() {
										handleResult(RequestStatus.Canceled);
									}

									@Override
									public void onException(Exception e) {
										handleResult(RequestStatus.Exception);
									}

									@Override
									public void onTimeout() {
										handleResult(RequestStatus.Timeout);
									}
								},
								timeout
						);
					} catch (Exception e) {
						Exception exp = new RuntimeException(String.format("Error launching sub action tagged %ss", subAction.getTag()), e);
						throw exp;
					}
				}
			} catch (Exception e) {
				if (!isRequestRunning()) {
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
				Exception exp = new RuntimeException(String.format("Error launching parallel request tagged %s", getTag()), e);
				setResultException(exp);
				setStatus(RequestStatus.Exception);
				for (Request<?> subAction : mSubActions) {
					if (subAction.isRequestRunning()) {
						try {
							subAction.cancel();
						} catch (Exception ee) {
							ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling sub action tagged %s  when failed to launch parallel request tagged %s", subAction.getTag(), getTag()), ee));
						}
					}
				}
				try {
					mCallback.onException(exp);
				} catch (Exception ee) {
					//ignore
				}
				return;
			}
		}
	}

	private void calc() {
		synchronized (mRequestLock) {
			if (!isRequestRunning()) {
				return;
			}
//			if (getTimeoutRemains() <= 0) {
//				Wnn.c(mTimeoutCheckTask, Task::cancel);
//				setStatus(RequestStatus.Timeout);
//				mCallback.onTimeout();
//				return;
//			}

			//子任务全部完成
			if (mSubActionResult.size() != mSubActions.size()) {
				return;
			}
			Wnn.c(mTimeoutCheckTask, Task::cancel);

			Set<Request.RequestStatus> resultSet = new HashSet<>(mSubActionResult.values());
			if (resultSet.contains(RequestStatus.Timeout)) {
				setStatus(RequestStatus.Timeout);
				try {
					mCallback.onTimeout();
				} catch (Exception e) {
					//ignore
				}
				return;
			}

			Ref<T> comprehensiveResultValue = new Ref<>();
			Ref<Exception> comprehensiveResultException = new Ref<>();
			RequestStatus comprehensiveResult = null;
			Exception exceptionInCompositor = null;
			try {
				comprehensiveResult = mValueCompositor.composite(new HashSet<>(mSubActions), comprehensiveResultValue, comprehensiveResultException);
			} catch (Exception e) {
				exceptionInCompositor = e;
			}
			if (exceptionInCompositor != null) {
				Exception combinedException = new RuntimeException("Exception occurred while generating comprehensive result value", exceptionInCompositor);
				setResultException(combinedException);
				setStatus(RequestStatus.Exception);
				try {
					mCallback.onException(combinedException);
				} catch (Exception e) {
					//ignore
				}
				return;
			}
			if (comprehensiveResult == null) {
				Exception combinedException = new NullPointerException("Value compositor generated null");
				setResultException(combinedException);
				setStatus(RequestStatus.Exception);
				try {
					mCallback.onException(combinedException);
				} catch (Exception e) {
					//ignore
				}
				return;
			}
			switch (comprehensiveResult) {
				case Done:
					setResultValue(comprehensiveResultValue.value);
					setStatus(RequestStatus.Done);
					try {
						mCallback.accept(comprehensiveResultValue.value);
					} catch (Exception e) {
						//ignore
					}
					return;
				case Exception:
					setResultException(comprehensiveResultException.value);
					setStatus(RequestStatus.Exception);
					try {
						mCallback.onException(comprehensiveResultException.value);
					} catch (Exception e) {
						//ignore
					}
					return;
				default:
					Exception exception = new RuntimeException(String.format("Unable handle current situation, comprehensive result: %s", comprehensiveResult));
					setResultException(exception);
					setStatus(RequestStatus.Exception);
					try {
						mCallback.onException(exception);
					} catch (Exception e) {
						//ignore
					}
					return;
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
			for (Request<?> subAction : mSubActions) {
				if (subAction.isRequestRunning()) {
					try {
						subAction.cancel();
					} catch (Exception e) {
						ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling sub action tagged %s for parallel request tagged %s when invoking cancel, calling chain: %s", subAction.getTag(), getTag(), subAction.generateCallChainInfo()), e));
					}
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
		return String.format("(Parallel Request) %s", getTag());
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

		RequestStatus composite(Collection<Request<?>> subActions, Ref<T> resultValue, Ref<Exception> resultException);

		default Exception compositeExceptions(Collection<Exception> exceptions) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%s exception occurred in sub actions:\n\n\n", exceptions.size()));
			for (Exception e : exceptions) {
				StringWriter stringWriter = new StringWriter();
				PrintWriter printWriter = new PrintWriter(stringWriter);
				e.printStackTrace(printWriter);
				sb.append(String.format("===========  %s  ==========\n", e.toString()));
				sb.append(stringWriter.getBuffer().toString()).append("\n\n");
			}
			return new RuntimeException(sb.toString());
		}
	}

}
