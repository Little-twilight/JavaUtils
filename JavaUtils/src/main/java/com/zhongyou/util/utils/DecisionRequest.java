package com.zhongyou.util.utils;

import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.function.BiConsumer;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.function.Consumer;
import com.zhongyou.util.function.Function;
import com.zhongyou.util.ref.BiRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionRequest<T> extends Request<T> {
	private final String TAG = DecisionRequest.class.getSimpleName();
	private Task mTimeoutCheckTask;
	private Callback<T> mCallback;

	private boolean mCancelingActionFlag;

	private final List<Node<?>> mDecisionNodes = new ArrayList<>();

	public <V> DecisionRequest(Request<V> initialNode, BiConsumer<Request<V>, DecisionMaker<T>> initialDecisionMakerHandler) {
		Objects.requireNonNull(initialNode);
		Objects.requireNonNull(initialDecisionMakerHandler);
		Node<V> nextNode = new Node<>(initialNode, initialDecisionMakerHandler);
		mDecisionNodes.add(nextNode);
	}

	@Override
	public void launch(Callback<T> callback, long timeout) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException(String.format("Launch a decision request tagged %s, which is in status %s", getTag(), getStatus()));
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
								Node currentNode = mDecisionNodes.get(mDecisionNodes.size() - 1);
								try {
									currentNode.cancel();
								} catch (Exception e) {
									ZyLogger.printException(TAG, new RuntimeException(String.format("Error canceling node tagged %s for decision request tagged %s when reaching timeout, calling chain: %s", currentNode.getTag(), getTag(), generateCallChainInfo()), e));
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
				Node node = mDecisionNodes.get(0);
				if (node == null) {
					throw new RuntimeException("No initial node found");
				}
				try {
					node.execute(getTimeoutRemains());
				} catch (Exception e) {
					throw new RuntimeException(String.format("Error executing initial node tagged %s", node.mNodeRequest.getTag()), e);
				}
			} catch (Exception e) {
				Exception exp = new RuntimeException(String.format("Error launching decision request tagged: %s", getTag()), e);
				if (!isRequestRunning()) {
					ZyLogger.printException(TAG, exp);
					return;
				}
				Wnn.c(mTimeoutCheckTask, Task::cancel);
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
			Node currentNode = mDecisionNodes.get(mDecisionNodes.size() - 1);
			mCancelingActionFlag = true;
			try {
				currentNode.cancel();
			} catch (Exception e) {
				throw new RuntimeException(String.format("Error canceling node tagged %s when cancel decision request tagged %s", currentNode.getTag(), getTag()), e);
			} finally {
				mCancelingActionFlag = false;
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

	private class Node<V> {
		private Request<V> mNodeRequest;
		private BiConsumer<Request<V>, DecisionMaker<T>> mDecisionMakerHandler;
		private DecisionMaker<T> mDecisionMaker = new DecisionMaker<T>() {
			@Override
			public <W> void doNextDecision(Request<W> currentNode, BiConsumer<Request<W>, DecisionMaker<T>> nextDecisionMakerHandler) {
				synchronized (mRequestLock) {
					if (!isRequestRunning()) {
						return;
					}
					Node<W> nextNode = new Node<>(currentNode, nextDecisionMakerHandler);
					mDecisionNodes.add(nextNode);
					DecisionRequest.this.scheduleTaskDelayed(
							() -> {
								synchronized (mRequestLock) {
									if (!isRequestRunning()) {
										return;
									}
									if (mDecisionNodes.get(mDecisionNodes.size() - 1) != nextNode) {
										return;
									}
									try {
										nextNode.execute(getTimeoutRemains());
									} catch (Exception e) {
										Exception exp = new RuntimeException(String.format("Error launch node tagged %s after node tagged %s", currentNode.getTag(), Node.this.getTag()), e);
										if (!isRequestRunning()) {
											ZyLogger.printException(TAG, exp);
											return;
										}
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
							},
							0
					);
				}
			}

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
				Exception e = new RuntimeException(String.format("Decision node tagged %s cancel abnormally", Node.this.getTag()));
				handelResult(RequestStatus.Exception,
						callback -> {
							Exception exp = new RuntimeException(String.format("Error executing decision request tagged %s", DecisionRequest.this.getTag()), e);
							setResultException(exp);
							callback.onException(exp);
						});
			}

			@Override
			public void onException(Exception e) {
				handelResult(RequestStatus.Exception,
						callback -> {
							Exception exp = new RuntimeException(String.format("Error executing node tagged %s", Node.this.getTag()), e);
							setResultException(exp);
							callback.onException(exp);
						});
			}

			@Override
			public void onTimeout() {
				handelResult(RequestStatus.Timeout, Callback::onTimeout);
			}
		};

		private Node(Request<V> nodeRequest, BiConsumer<Request<V>, DecisionMaker<T>> decisionMakerHandler) {
			mNodeRequest = nodeRequest;
			mDecisionMakerHandler = decisionMakerHandler;
		}

		private void execute(long timeout) {
			Request.RequestStatus requestStatus = Node.this.getStatus();
			if (requestStatus != RequestStatus.Idle) {
				throw new RuntimeException(String.format("Execute node which is in status %s", requestStatus));
			}
			List<Request> callingChain = new ArrayList<>();
			callingChain.addAll(DecisionRequest.this.getCallingChain());
			callingChain.add(DecisionRequest.this);
			mNodeRequest.setupCallingChain(callingChain);
			mNodeRequest.setTaskScheduler(DecisionRequest.this.getTaskScheduler());
			mNodeRequest.launch(
					new Callback<V>() {
						private void handleDecision() {
							synchronized (mRequestLock) {
								if (!isRequestRunning()) {
									return;
								}
								if (mDecisionNodes.get(mDecisionNodes.size() - 1) != Node.this) {
									//not current decision node;
									return;
								}
								try {
									mDecisionMakerHandler.accept(mNodeRequest, mDecisionMaker);
								} catch (Exception e) {
									Exception exp = new RuntimeException(String.format("Error making decision on decision node tagged %s", Node.this.getTag()), e);
									if (!isRequestRunning()) {
										ZyLogger.printException(TAG, exp);
										return;
									}
									setStatus(RequestStatus.Exception);
									setResultException(exp);
									Wnn.c(mTimeoutCheckTask, Task::cancel);
									if (mCallback != null) {
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
						public void accept(V v) {
							handleDecision();
						}

						@Override
						public void onCanceled() {
							handleDecision();
						}

						@Override
						public void onException(Exception e) {
							handleDecision();
						}

						@Override
						public void onTimeout() {
							handleDecision();
						}
					},
					timeout
			);
		}

		private void cancel() {
			Request.RequestStatus requestStatus = Node.this.getStatus();
			if (requestStatus != RequestStatus.Pending) {
				throw new RuntimeException(String.format("Cancel node which is in status %s", requestStatus));
			}
			mNodeRequest.cancel();
		}

		private String getTag() {
			return mNodeRequest.getTag();
		}

		private String dumpCallingStatus() {
			return mNodeRequest.dumpCallingStatus();
		}

		private RequestStatus getStatus() {
			return mNodeRequest.getStatus();
		}

	}

	public interface DecisionMaker<V> extends Callback<V> {
		<W> void doNextDecision(Request<W> nextNode, BiConsumer<Request<W>, DecisionMaker<V>> nextDecisionMakerHandler);
	}

	@Override
	protected String generateCallingInfo() {
		return String.format("(Decision Request) %s", getTag());
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
			for (Node node : mDecisionNodes) {
				subActionStatus.add(node.dumpCallingStatus());
			}
			return concatDumpTree(String.format("%s:: %s", generateCallingInfo(), content), subActionStatus);
		}
	}

	public static <V, W, T> BiConsumer<Request<V>, DecisionRequest.DecisionMaker<T>> handleDecision(
			Function<Request<V>, BiRef<Request<W>, BiConsumer<Request<W>, DecisionMaker<T>>>> decision) {
		return (request, decisionMaker) -> {
			Request.RequestStatus status = request.getStatus();
			switch (status) {
				case Pending:
				case Idle:
					break;
				case Canceled:
					decisionMaker.onCanceled();
					break;
				case Timeout:
					decisionMaker.onTimeout();
					break;
				case Exception:
					decisionMaker.onException(request.getResultException());
					break;
				case Done:
					BiRef<Request<W>, BiConsumer<Request<W>, DecisionRequest.DecisionMaker<T>>> nextDecision = decision.apply(request);
					decisionMaker.doNextDecision(nextDecision.getFirst(), nextDecision.getSecond());
					break;
				default:
					decisionMaker.onException(new RuntimeException(String.format("Unsupported: %s", status)));
					break;
			}
		};
	}

	public static <V, T> BiConsumer<Request<V>, DecisionRequest.DecisionMaker<T>> handleFinalDecision(
			Function<Request<V>, T> decision) {
		return (request, decisionMaker) -> {
			Request.RequestStatus status = request.getStatus();
			switch (status) {
				case Pending:
				case Idle:
					break;
				case Canceled:
					decisionMaker.onCanceled();
					break;
				case Timeout:
					decisionMaker.onTimeout();
					break;
				case Exception:
					decisionMaker.onException(request.getResultException());
					break;
				case Done:
					decisionMaker.accept(decision.apply(request));
					break;
				default:
					decisionMaker.onException(new RuntimeException(String.format("Unsupported: %s", status)));
					break;
			}
		};
	}

}
