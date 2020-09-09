package com.zhongyou.util.utils;

import com.zhongyou.util.function.BiConsumer;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.ref.BiRef;

import org.junit.Assert;
import org.junit.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

public class DecisionRequestTest {

	private static Timer sTimer = new Timer();

	@Test
	public void testNormal() throws InterruptedException {
		BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> initialNode = generateNode(0, 10);
		DecisionRequest<Boolean> decisionRequest = new DecisionRequest<>(initialNode.getFirst(), initialNode.getSecond());
		decisionRequest.setTag("Decision test normal");
		Semaphore semaphore = new Semaphore(0);
		decisionRequest.launch(
				new Callback<Boolean>() {

					private void doFinal() {
						semaphore.release();
					}

					@Override
					public void accept(Boolean ret) {
						doFinal();
					}

					@Override
					public void onCanceled() {
						doFinal();
					}

					@Override
					public void onException(Exception e) {
						System.out.println(decisionRequest.dumpCallingStatus());
						doFinal();
					}

					@Override
					public void onTimeout() {
						doFinal();
					}
				}
		);
		semaphore.acquire();
		Assert.assertEquals(Request.RequestStatus.Done, decisionRequest.getStatus());
	}

	private static BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> generateNode(int value, int bound) {
		SimpleRequest<Integer> simpleRequest = new SimpleRequest<>(new SimpleRequest.RequestAction<Integer>() {
			@Override
			public void execute(Callback<Integer> callback, long timeout) {
				callback.accept(value);
			}

			@Override
			public void cancel() {

			}
		});
		simpleRequest.setTag(String.format("Node-%s", value));
		return BiRef.create(
				simpleRequest,
				(request, decisionMaker) -> {
					System.out.println(String.format("%s: %s", request.getTag(), request.getStatus()));
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
							if (value < bound) {
								int newValue = value + 1;
								BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> nextNode = generateNode(newValue, bound);
								decisionMaker.doNextDecision(nextNode.getFirst(), nextNode.getSecond());
							} else {
								decisionMaker.accept(true);
							}
							break;
						default:
							decisionMaker.onException(new RuntimeException(String.format("Unsupported: %s", status)));
							break;
					}
				}
		);
	}

	@Test
	public void testCancel() throws InterruptedException {
		long costPerNode = 1000L;
		BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> initialNode = generateNodeCancel(costPerNode, 0, 10);
		DecisionRequest<Boolean> decisionRequest = new DecisionRequest<>(initialNode.getFirst(), initialNode.getSecond());
		decisionRequest.setTag("Decision test cancel");
		Semaphore semaphore = new Semaphore(0);
		sTimer.schedule(
				new TimerTask() {
					@Override
					public void run() {
						decisionRequest.cancel();
					}
				},
				costPerNode * 5
		);
		decisionRequest.launch(
				new Callback<Boolean>() {

					private void doFinal() {
						semaphore.release();
					}

					@Override
					public void accept(Boolean ret) {
						doFinal();
					}

					@Override
					public void onCanceled() {
						doFinal();
					}

					@Override
					public void onException(Exception e) {
						doFinal();
					}

					@Override
					public void onTimeout() {
						doFinal();
					}
				}
		);
		semaphore.acquire();
		Assert.assertEquals(Request.RequestStatus.Canceled, decisionRequest.getStatus());
		System.out.println(decisionRequest.dumpCallingStatus());
	}

	private static BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> generateNodeCancel(long costPerNode, int value, int bound) {
		SimpleRequest<Integer> simpleRequest = new SimpleRequest<>(new SimpleRequest.RequestAction<Integer>() {
			@Override
			public void execute(Callback<Integer> callback, long timeout) {
				sTimer.schedule(
						new TimerTask() {
							@Override
							public void run() {
								callback.accept(value);

							}
						},
						costPerNode
				);
			}

			@Override
			public void cancel() {

			}
		});
		simpleRequest.setTag(String.format("Node-%s", value));
		return BiRef.create(
				simpleRequest,
				(request, decisionMaker) -> {
					System.out.println(String.format("%s: %s", request.getTag(), request.getStatus()));
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
							if (value < bound) {
								int newValue = value + 1;
								BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> nextNode = generateNodeCancel(costPerNode, newValue, bound);
								decisionMaker.doNextDecision(nextNode.getFirst(), nextNode.getSecond());
							} else {
								decisionMaker.accept(true);
							}
							break;
						default:
							decisionMaker.onException(new RuntimeException(String.format("Unsupported: %s", status)));
							break;
					}
				}
		);
	}

	@Test
	public void testTimeout() throws InterruptedException {
		long costPerNode = 1000L;
		BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> initialNode = generateNodeTimeout(costPerNode, 0, 10);
		DecisionRequest<Boolean> decisionRequest = new DecisionRequest<>(initialNode.getFirst(), initialNode.getSecond());
		decisionRequest.setTag("Decision test timeout");
		Semaphore semaphore = new Semaphore(0);
		decisionRequest.launch(
				new Callback<Boolean>() {

					private void doFinal() {
						semaphore.release();
					}

					@Override
					public void accept(Boolean ret) {
						doFinal();
					}

					@Override
					public void onCanceled() {
						doFinal();
					}

					@Override
					public void onException(Exception e) {
						doFinal();
					}

					@Override
					public void onTimeout() {
						doFinal();
					}
				},
				costPerNode * 5
		);
		semaphore.acquire();
		Assert.assertEquals(Request.RequestStatus.Timeout, decisionRequest.getStatus());
		System.out.println(decisionRequest.dumpCallingStatus());
	}

	private static BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> generateNodeTimeout(long costPerNode, int value, int bound) {
		SimpleRequest<Integer> simpleRequest = new SimpleRequest<>(new SimpleRequest.RequestAction<Integer>() {
			@Override
			public void execute(Callback<Integer> callback, long timeout) {
				sTimer.schedule(
						new TimerTask() {
							@Override
							public void run() {
								callback.accept(value);

							}
						},
						costPerNode
				);
			}

			@Override
			public void cancel() {

			}
		});
		simpleRequest.setTag(String.format("Node-%s", value));
		return BiRef.create(
				simpleRequest,
				(request, decisionMaker) -> {
					System.out.println(String.format("%s: %s", request.getTag(), request.getStatus()));
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
							if (value < bound) {
								int newValue = value + 1;
								BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> nextNode = generateNodeTimeout(costPerNode, newValue, bound);
								decisionMaker.doNextDecision(nextNode.getFirst(), nextNode.getSecond());
							} else {
								decisionMaker.accept(true);
							}
							break;
						default:
							decisionMaker.onException(new RuntimeException(String.format("Unsupported: %s", status)));
							break;
					}
				}
		);
	}

	@Test
	public void testException() throws InterruptedException {
		long costPerNode = 1000L;
		BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> initialNode = generateNodeException(costPerNode, 0, 10);
		DecisionRequest<Boolean> decisionRequest = new DecisionRequest<>(initialNode.getFirst(), initialNode.getSecond());
		decisionRequest.setTag("Decision test exception");
		Semaphore semaphore = new Semaphore(0);
		decisionRequest.launch(
				new Callback<Boolean>() {

					private void doFinal() {
						semaphore.release();
					}

					@Override
					public void accept(Boolean ret) {
						doFinal();
					}

					@Override
					public void onCanceled() {
						doFinal();
					}

					@Override
					public void onException(Exception e) {
						doFinal();
					}

					@Override
					public void onTimeout() {
						doFinal();
					}
				}
		);
		semaphore.acquire();
		Assert.assertEquals(Request.RequestStatus.Exception, decisionRequest.getStatus());
		System.out.println(decisionRequest.dumpCallingStatus());
	}

	private static BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> generateNodeException(long costPerNode, int value, int bound) {
		SimpleRequest<Integer> simpleRequest = new SimpleRequest<>(new SimpleRequest.RequestAction<Integer>() {
			@Override
			public void execute(Callback<Integer> callback, long timeout) {
				sTimer.schedule(
						new TimerTask() {
							@Override
							public void run() {
								if(value==bound){
									callback.onException(new RuntimeException("Test exception"));
								}else {
									callback.accept(value);
								}

							}
						},
						costPerNode
				);
			}

			@Override
			public void cancel() {

			}
		});
		simpleRequest.setTag(String.format("Node-%s", value));
		return BiRef.create(
				simpleRequest,
				(request, decisionMaker) -> {
					System.out.println(String.format("%s: %s", request.getTag(), request.getStatus()));
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
							if (value < bound) {
								int newValue = value + 1;
								BiRef<Request<Integer>, BiConsumer<Request<Integer>, DecisionRequest.DecisionMaker<Boolean>>> nextNode = generateNodeException(costPerNode, newValue, bound);
								decisionMaker.doNextDecision(nextNode.getFirst(), nextNode.getSecond());
							} else {
								decisionMaker.accept(true);
							}
							break;
						default:
							decisionMaker.onException(new RuntimeException(String.format("Unsupported: %s", status)));
							break;
					}
				}
		);
	}

}