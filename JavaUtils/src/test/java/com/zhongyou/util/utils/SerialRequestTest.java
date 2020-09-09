package com.zhongyou.util.utils;

import com.zhongyou.util.function.Callback;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class SerialRequestTest {

    @Test
    public void testLaunch() throws InterruptedException {
        Random random = new Random();
        int total = 100;
        Semaphore semaphore = new Semaphore(0);
        Timer timer = new Timer();
        for (int i = 0; i < total; i++) {
            int iRef = i;
            long now = System.currentTimeMillis();
            AtomicInteger counter = new AtomicInteger();
            SerialRequest<Integer> serialRequest = new SerialRequest<>(
                    subActions -> {
                        int value = 0;
                        for (Request subAction : subActions) {
                            value += (Integer) subAction.getResultValue();
                        }
                        return value;
                    }
            );
            int subActionCount = random.nextInt(100) + 1;
            List<Request<?>> requests = new ArrayList<>();
            for (int j = 0; j < subActionCount; j++) {
                int value = random.nextInt(subActionCount);
                long delay = random.nextInt(100);
                String msg = String.format("Round %s sub action %s executed", i, j);
                requests.add(
                        new SimpleRequest<>(
                                new SimpleRequest.RequestAction<Object>() {
                                    @Override
                                    public void execute(Callback<Object> callback, long timeout) {
                                        System.out.println(msg);
                                        Runnable action = () -> {
                                            counter.addAndGet(value);
                                            callback.accept(value);
                                        };
                                        timer.schedule(
                                                new TimerTask() {
                                                    @Override
                                                    public void run() {
                                                        action.run();
                                                    }
                                                },
                                                delay
                                        );
//                                        action.run();
                                    }

                                    @Override
                                    public void cancel() {

                                    }
                                }
                        )
                );
            }
            serialRequest.setupSubActions(requests);
            serialRequest.launch(
                    val -> {
                        semaphore.release();
                        System.out.println(String.format("Round %s, expect value %s, actual value %s", iRef, counter.get(), val));
                        Assert.assertEquals(counter.get(), val.intValue());
                    }
            );
        }
        semaphore.acquire(total);
        System.out.println(String.format("%s tests accomplished", total));
    }

    @Test
    public void testExtra() throws InterruptedException {
        Random random = new Random();
        int total = 1;
        Semaphore semaphore = new Semaphore(0);
        Timer timer = new Timer();
        Request.RequestStatus[] samples = {
                Request.RequestStatus.Done,
                Request.RequestStatus.Exception,
                Request.RequestStatus.Timeout,
                Request.RequestStatus.Canceled
        };
        for (int i = 0; i < total; i++) {
            int iRef = i;
            long now = System.currentTimeMillis();
            Request.RequestStatus routine = samples[random.nextInt(samples.length)];
            System.out.println(String.format("Round %s routine %s", i, routine));
            AtomicInteger counter = new AtomicInteger();
            SerialRequest<Integer> request = new SerialRequest<>(
                    subActions -> {
                        int value = 0;
                        for (Request subAction : subActions) {
                            value += (Integer) subAction.getResultValue();
                        }
                        return value;
                    }
            );
            int subActionCount = random.nextInt(100) + 1;
            List<Request<?>> requests = new ArrayList<>();
            long totalDelay = 10000L;
            long remainingDelay = totalDelay;
            System.out.println(String.format("Round %s schedule %s sub actions", i, subActionCount));
            for (int j = 0; j < subActionCount; j++) {
                int value = random.nextInt(subActionCount);
                long delay = random.nextInt((int) remainingDelay);
                remainingDelay -= delay;
                String msg = String.format("Round %s sub action %s executed", i, j);
                requests.add(
                        new SimpleRequest<>(
                                new SimpleRequest.RequestAction<Object>() {
                                    @Override
                                    public void execute(Callback<Object> callback, long timeout) {
                                        System.out.println(msg);
                                        Runnable action = () -> {
                                            switch (routine) {
                                                case Done:
                                                    counter.addAndGet(value);
                                                    callback.accept(value);
                                                    break;
                                                case Timeout:
//                                                    callback.onTimeout();
                                                    break;
                                                case Canceled:
//                                                    callback.onCanceled();
                                                    break;
                                                case Exception:
                                                default:
                                                    if (random.nextBoolean()) {
                                                        counter.addAndGet(value);
                                                        callback.accept(value);
                                                    } else {
                                                        callback.onException(new RuntimeException());
                                                    }
                                                    break;
                                            }
                                        };
                                        timer.schedule(
                                                new TimerTask() {
                                                    @Override
                                                    public void run() {
                                                        action.run();
                                                    }
                                                },
                                                delay
                                        );
//                                        action.run();
                                    }

                                    @Override
                                    public void cancel() {

                                    }
                                }
                        )
                );
            }
            request.setupSubActions(requests);
            switch (routine) {
                case Canceled:
                    timer.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    System.out.println(String.format("Try to cancel round %s", iRef));
                                    request.cancel();
                                }
                            },
                            totalDelay / 2
                    );
                    break;
            }
            request.launch(
                    new Callback<Integer>() {
                        @Override
                        public void accept(Integer val) {
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s", iRef, routine, Request.RequestStatus.Done));
                            Assert.assertEquals(routine, Request.RequestStatus.Done);
                            System.out.println(String.format("Round %s, expect value %s, actual value %s, expect delay %s, actual delay %s", iRef, counter.get(), val, totalDelay, System.currentTimeMillis() - now));
                            Assert.assertEquals(counter.get(), val.intValue());
                        }

                        @Override
                        public void onCanceled() {
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s", iRef, routine, Request.RequestStatus.Canceled));
                            Assert.assertEquals(routine, Request.RequestStatus.Canceled);
                        }

                        @Override
                        public void onException(Exception e) {
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s", iRef, routine, Request.RequestStatus.Exception));
                            Assert.assertEquals(routine, Request.RequestStatus.Exception);
                        }

                        @Override
                        public void onTimeout() {
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s", iRef, routine, Request.RequestStatus.Timeout));
                            Assert.assertEquals(routine, Request.RequestStatus.Timeout);
                        }
                    },
                    totalDelay
            );
        }
        semaphore.acquire(total);
        System.out.println(String.format("%s tests accomplished", total));
    }

}