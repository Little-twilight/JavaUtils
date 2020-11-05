package com.macfred.util.utils;

import com.macfred.util.function.Callback;
import com.macfred.util.function.Supplier;
import com.macfred.util.ref.Ref;

import org.junit.Assert;
import org.junit.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class AtomicRequestTest {

	private static final Timer sTimer = new Timer();

	private static <T, V> void skeleton(SimpleRequest.RequestAction<T> majorActionBody, long majorTimeout, Ref<AtomicRequest<T, V>> atomicRequestRef, AtomicRequest.Helper<V> helper, Supplier<Boolean> exitTester) throws InterruptedException {
		SimpleRequest<T> majorAction = new SimpleRequest<>(majorActionBody);
		AtomicRequest<T, V> atomicRequest = new AtomicRequest<>(majorAction, helper);
		atomicRequestRef.value = atomicRequest;
		atomicRequest.launch(
				new Callback<T>() {
					@Override
					public void accept(T ret) {
						System.out.println(String.format("Major action accept :%s", ret));
					}

					@Override
					public void onCanceled() {
						System.out.println("Major action on cancel");
					}

					@Override
					public void onException(Exception e) {
						System.out.println(String.format("Major action onException :%s", e));
					}

					@Override
					public void onTimeout() {
						System.out.println("Major action on timeout");
					}
				},
				majorTimeout
		);
		while (!exitTester.get()) {
			Thread.sleep(1000L);
		}
	}

	private static <T, V> void skeleton(SimpleRequest.RequestAction<T> majorActionBody, long majorTimeout, SimpleRequest.RequestAction<V> rollbackActionBody, long rollbackTimeout, Ref<AtomicRequest<T, V>> atomicRequestRef) throws InterruptedException {
		AtomicBoolean lock = new AtomicBoolean();
		skeleton(
				majorActionBody,
				majorTimeout,
				atomicRequestRef,
				new AtomicRequest.Helper<V>() {
					@Override
					public boolean isResourceAcquired() {
						boolean ret = lock.get();
						System.out.println(String.format("isResourceAcquired :%s", ret));
						return ret;
					}

					@Override
					public boolean acquireResource() {
						boolean ret = lock.compareAndSet(false, true);
						System.out.println(String.format("acquireResource :%s", ret));
						return ret;
					}

					@Override
					public boolean releaseResource() {
						boolean ret = lock.compareAndSet(true, false);
						System.out.println(String.format("releaseResource :%s", ret));
						return ret;
					}

					@Override
					public void generateRollbackRequest(Ref<Request<V>> rollbackActionRef, Ref<Callback<V>> rollbackCallbackRef, Ref<Long> rollbackTimeoutRef) {
						rollbackActionRef.value = new SimpleRequest<>(rollbackActionBody);
						rollbackCallbackRef.value = new Callback<V>() {
							@Override
							public void accept(V ret) {
								System.out.println(String.format("Rollback action accept :%s", ret));
							}

							@Override
							public void onCanceled() {
								System.out.println("Rollback action on cancel");
							}

							@Override
							public void onException(Exception e) {
								System.out.println(String.format("Rollback action onException :%s", e));
							}

							@Override
							public void onTimeout() {
								System.out.println("Rollback action on timeout");
							}
						};
						rollbackTimeoutRef.value = rollbackTimeout;
					}
				},
				() -> !lock.get()
		);
	}

	@Test
	public void testMajorActionNormal() throws InterruptedException {
		System.out.println("=======    testMajorActionNormal begin    ======");
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						callback.accept(1000);
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new SimpleRequest.RequestAction<Boolean>() {
					@Override
					public void execute(Callback<Boolean> callback, long timeout) {
						callback.accept(true);
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new Ref<>()
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testMajorActionException() throws InterruptedException {
		System.out.println("=======    testMajorActionException begin    ======");
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						callback.onException(new RuntimeException("Test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new SimpleRequest.RequestAction<Boolean>() {
					@Override
					public void execute(Callback<Boolean> callback, long timeout) {
						callback.accept(true);
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new Ref<>()
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testMajorActionTimeout() throws InterruptedException {
		System.out.println("=======    testMajorActionTimeout begin    ======");
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						System.out.println("Major action triggered");
//						callback.onException(new RuntimeException("Test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new SimpleRequest.RequestAction<Boolean>() {
					@Override
					public void execute(Callback<Boolean> callback, long timeout) {
						callback.accept(true);
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new Ref<>()
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testMajorActionCancel() throws InterruptedException {
		System.out.println("=======    testMajorActionCancel begin    ======");
		Ref<AtomicRequest<Integer, Boolean>> atomicRequestRef = new Ref<>();
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						System.out.println("Major action triggered");
						sTimer.schedule(
								new TimerTask() {
									@Override
									public void run() {
										System.out.println("Cancel major action");
										if (atomicRequestRef.value.getStatus() == Request.RequestStatus.Pending) {
											atomicRequestRef.value.cancel();
											Assert.assertEquals(Request.RequestStatus.Canceled, atomicRequestRef.value.getStatus());
										}
									}
								},
								4500L
						);
//						callback.onException(new RuntimeException("Test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new SimpleRequest.RequestAction<Boolean>() {
					@Override
					public void execute(Callback<Boolean> callback, long timeout) {
						callback.accept(true);
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				atomicRequestRef
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testRollbackActionException() throws InterruptedException {
		System.out.println("=======    testRollbackActionException begin    ======");
		Ref<AtomicRequest<Integer, Boolean>> atomicRequestRef = new Ref<>();
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						callback.onException(new RuntimeException("Major action test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new SimpleRequest.RequestAction<Boolean>() {
					@Override
					public void execute(Callback<Boolean> callback, long timeout) {
						callback.onException(new RuntimeException("Rollback action test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				atomicRequestRef
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testRollbackActionTimeout() throws InterruptedException {
		System.out.println("=======    testRollbackActionTimeout begin    ======");
		Ref<AtomicRequest<Integer, Boolean>> atomicRequestRef = new Ref<>();
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						callback.onException(new RuntimeException("Major action test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				new SimpleRequest.RequestAction<Boolean>() {
					@Override
					public void execute(Callback<Boolean> callback, long timeout) {
						System.out.println("Rollback action triggered");
						//do nothing
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				atomicRequestRef
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testAcquireResource() throws InterruptedException {
		System.out.println("=======    testAcquireResource begin    ======");
		Ref<AtomicRequest<Integer, Boolean>> atomicRequestRef = new Ref<>();
		AtomicBoolean lock = new AtomicBoolean();
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						callback.onException(new RuntimeException("Major action test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				atomicRequestRef,
				new AtomicRequest.Helper<Boolean>() {
					@Override
					public boolean isResourceAcquired() {
						boolean ret = lock.get();
						System.out.println(String.format("isResourceAcquired :%s", ret));
						return ret;
					}

					@Override
					public boolean acquireResource() {
						System.out.println("acquireResource return failure");
						return false;
//						boolean ret = lock.compareAndSet(false, true);
//						System.out.println(String.format("acquireResource :%s", ret));
//						return ret;
					}

					@Override
					public boolean releaseResource() {
						boolean ret = lock.compareAndSet(true, false);
						System.out.println(String.format("releaseResource :%s", ret));
						return ret;
					}

					@Override
					public void generateRollbackRequest(Ref<Request<Boolean>> rollbackActionRef, Ref<Callback<Boolean>> rollbackCallbackRef, Ref<Long> rollbackTimeoutRef) {
						rollbackActionRef.value = new SimpleRequest<>(
								new SimpleRequest.RequestAction<Boolean>() {
									@Override
									public void execute(Callback<Boolean> callback, long timeout) {
										System.out.println("Rollback action triggered");
										//do nothing
									}

									@Override
									public void cancel() {

									}
								}
						);
						rollbackCallbackRef.value = new Callback<Boolean>() {
							@Override
							public void accept(Boolean ret) {
								System.out.println(String.format("Rollback action accept :%s", ret));
							}

							@Override
							public void onCanceled() {
								System.out.println("Rollback action on cancel");
							}

							@Override
							public void onException(Exception e) {
								System.out.println(String.format("Rollback action onException :%s", e));
							}

							@Override
							public void onTimeout() {
								System.out.println("Rollback action on timeout");
							}
						};
						rollbackTimeoutRef.value = 5000L;
					}
				},
				() -> !lock.get()
		);
		System.out.println("================================================\n");
	}

	@Test
	public void testReleaseResource() throws InterruptedException {
		System.out.println("=======    testAcquireResource begin    ======");
		Ref<AtomicRequest<Integer, Boolean>> atomicRequestRef = new Ref<>();
		AtomicBoolean lock = new AtomicBoolean();
		skeleton(
				new SimpleRequest.RequestAction<Integer>() {
					@Override
					public void execute(Callback<Integer> callback, long timeout) {
						callback.onException(new RuntimeException("Major action test exception"));
					}

					@Override
					public void cancel() {

					}
				},
				5000L,
				atomicRequestRef,
				new AtomicRequest.Helper<Boolean>() {
					@Override
					public boolean isResourceAcquired() {
						boolean ret = lock.get();
						System.out.println(String.format("isResourceAcquired :%s", ret));
						return ret;
					}

					@Override
					public boolean acquireResource() {
						boolean ret = lock.compareAndSet(false, true);
						System.out.println(String.format("acquireResource :%s", ret));
						return ret;
					}

					@Override
					public boolean releaseResource() {
						System.out.println("releaseResource arise exception");
						throw new RuntimeException("Test exception in releaseResource");
//						boolean ret = lock.compareAndSet(true, false);
//						System.out.println(String.format("releaseResource :%s", ret));
					}

					@Override
					public void generateRollbackRequest(Ref<Request<Boolean>> rollbackActionRef, Ref<Callback<Boolean>> rollbackCallbackRef, Ref<Long> rollbackTimeoutRef) {
						rollbackActionRef.value = new SimpleRequest<>(
								new SimpleRequest.RequestAction<Boolean>() {
									@Override
									public void execute(Callback<Boolean> callback, long timeout) {
										System.out.println("Rollback action triggered");
										//do nothing
									}

									@Override
									public void cancel() {

									}
								}
						);
						rollbackCallbackRef.value = new Callback<Boolean>() {
							@Override
							public void accept(Boolean ret) {
								System.out.println(String.format("Rollback action accept :%s", ret));
							}

							@Override
							public void onCanceled() {
								System.out.println("Rollback action on cancel");
							}

							@Override
							public void onException(Exception e) {
								System.out.println(String.format("Rollback action onException :%s", e));
							}

							@Override
							public void onTimeout() {
								System.out.println("Rollback action on timeout");
							}
						};
						rollbackTimeoutRef.value = 5000L;
					}
				},
				() -> !atomicRequestRef.value.isRequestRunning() && (Wnn.f(atomicRequestRef.value, request -> !request.isRequestRunning(), AtomicRequest::getRollbackAction, true))
		);
		System.out.println("================================================\n");
	}

}