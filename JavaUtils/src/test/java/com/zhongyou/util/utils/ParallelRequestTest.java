package com.zhongyou.util.utils;

import com.zhongyou.util.function.Callback;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelRequestTest {

    @Test
    public void testLaunch() throws InterruptedException {
        Random random = new Random();
        int total = 1000;
        Semaphore semaphore = new Semaphore(0);
        Timer timer = new Timer();
        for (int i = 0; i < total; i++) {
            int iRef = i;
            long now = System.currentTimeMillis();
            AtomicInteger counter = new AtomicInteger();
            ParallelRequest<Integer> parallelRequest = new ParallelRequest<>(
                    (subActions, resultValue, resultException) -> {
                        int value = 0;
                        for (Request subAction : subActions) {
                            value += (Integer) subAction.getResultValue();
                        }
                        resultValue.value = value;
                        return Request.RequestStatus.Done;
                    }
            );
            long maxDelay = 10000L;
            int subActionCount = random.nextInt(100) + 1;
            List<Request<?>> requests = new ArrayList<>();
            for (int j = 0; j < subActionCount; j++) {
                int value = random.nextInt(subActionCount);
                long delay = random.nextInt((int) maxDelay);
//                String msg = String.format("Round %s sub action %s executed", i, j);
                requests.add(
                        new SimpleRequest<>(
                                new SimpleRequest.RequestAction<Object>() {
                                    @Override
                                    public void execute(Callback<Object> callback, long timeout) {
//                                        System.out.println(msg);
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
            parallelRequest.setupSubActions(requests);
            parallelRequest.launch(
                    val -> {
                        semaphore.release();
                        System.out.println(String.format("Round %s, expect value %s, actual value %s", iRef, counter.get(), val));
                        Assert.assertEquals(counter.get(), val.intValue());
                    },
                    maxDelay
            );
        }
        semaphore.acquire(total);
        System.out.println(String.format("%s tests accomplished", total));
    }

    @Test
    public void testExtra() throws InterruptedException {
        Timer timeoutCheckTimer = new Timer();
        Random random = new Random();
        int total = 1000;
        Semaphore semaphore = new Semaphore(0);
        Timer timer = new Timer();
        Request.RequestStatus[] samples = {
                Request.RequestStatus.Done,
                Request.RequestStatus.Exception,
                Request.RequestStatus.Timeout,
                Request.RequestStatus.Canceled
        };
        AtomicInteger timeoutCheckCount = new AtomicInteger();
        Set<Request> finishedSamples = new HashSet<>();
        for (int i = 0; i < total; i++) {
            int iRef = i;
            long now = System.currentTimeMillis();
            Request.RequestStatus routine = samples[random.nextInt(samples.length)];
            System.out.println(String.format("Round %s routine %s", i, routine));
            AtomicInteger counter = new AtomicInteger();
            ParallelRequest<Integer> request = new ParallelRequest<>(
                    (subActions, resultValue, resultException) -> {
                        switch (routine) {
                            case Done:
                                int value = 0;
                                for (Request subAction : subActions) {
                                    value += (Integer) subAction.getResultValue();
                                }
                                resultValue.value = value;
                                return Request.RequestStatus.Done;
                            default:
                                return routine;

                        }

                    }
            );
            int subActionCount = random.nextInt(100) + 1;
            List<Request<?>> requests = new ArrayList<>();
            long totalDelay = 10000L;
            System.out.println(String.format("Round %s schedule %s sub actions", i, subActionCount));
            for (int j = 0; j < subActionCount; j++) {
                int value = random.nextInt(subActionCount);
                long delay = random.nextInt((int) totalDelay);
//                String msg = String.format("Round %s sub action %s executed", i, j);
                boolean isFinalSubAction = (j + 1) == subActionCount;
                AtomicBoolean timeoutSubActionScheduled;
                requests.add(
                        new SimpleRequest<>(
                                new SimpleRequest.RequestAction<Object>() {
                                    @Override
                                    public void execute(Callback<Object> callback, long timeout) {
//                                        System.out.println(msg);
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
                                                    if (random.nextBoolean() || isFinalSubAction) {
                                                        callback.onException(new RuntimeException());
                                                    } else {
                                                        counter.addAndGet(value);
                                                        callback.accept(value);
                                                    }
                                                    break;
                                            }
                                        };
                                        timer.schedule(
                                                new TimerTask() {
                                                    @Override
                                                    public void run() {
                                                        try {
                                                            action.run();
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                },
                                                Request.RequestStatus.Timeout.equals(routine) ? (random.nextBoolean() ? delay * 2 : delay) : delay / 2
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
                            finishedSamples.add(request);
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s, %s round finished up now", iRef, routine, Request.RequestStatus.Done, semaphore.availablePermits()));
                            Assert.assertEquals(routine, Request.RequestStatus.Done);
                            System.out.println(String.format("Round %s, expect value %s, actual value %s, expect delay %s, actual delay %s", iRef, counter.get(), val, totalDelay, System.currentTimeMillis() - now));
                            Assert.assertEquals(counter.get(), val.intValue());
                        }

                        @Override
                        public void onCanceled() {
                            finishedSamples.add(request);
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s, %s round finished up now", iRef, routine, Request.RequestStatus.Canceled, semaphore.availablePermits()));
                            Assert.assertEquals(routine, Request.RequestStatus.Canceled);
                        }

                        @Override
                        public void onException(Exception e) {
                            finishedSamples.add(request);
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s, %s round finished up now", iRef, routine, Request.RequestStatus.Exception, semaphore.availablePermits()));
                            Assert.assertEquals(routine, Request.RequestStatus.Exception);
                        }

                        @Override
                        public void onTimeout() {
                            finishedSamples.add(request);
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s, %s round finished up now", iRef, routine, Request.RequestStatus.Timeout, semaphore.availablePermits()));
                            Assert.assertEquals(routine, Request.RequestStatus.Timeout);
                        }
                    },
                    totalDelay
            );
            timeoutCheckTimer.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            if (request.getStatus() == Request.RequestStatus.Pending) {
                                System.out.println(String.format("%s timeout check, round %s detected running, finished mark found %s, routine %s, scheduled timeout %s, remaining timeout %s, %s round finished up now", timeoutCheckCount.getAndIncrement(), iRef, finishedSamples.contains(request), routine, request.getTimeout(), request.getTimeoutRemains(), semaphore.availablePermits()));
                            }
                        }
                    },
                    totalDelay * 2
            );
        }
        semaphore.acquire(total);
        System.out.println(String.format("%s tests accomplished", total));
    }

}