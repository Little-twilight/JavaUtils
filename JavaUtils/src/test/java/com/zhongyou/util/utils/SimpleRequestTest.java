package com.zhongyou.util.utils;

import com.zhongyou.util.function.Callback;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SimpleRequestTest {

    @Test
    public void testLaunch() throws InterruptedException {
        Random random = new Random();
        int total = 1000;
        Semaphore semaphore = new Semaphore(0);
        Timer timer = new Timer();
        for (int i = 0; i < total; i++) {
            int value = random.nextInt(total);
            int iRef = i;
            long now = System.currentTimeMillis();
            long delay = random.nextInt(5000);
            SimpleRequest<Integer> simpleRequest = new SimpleRequest<>(
                    new SimpleRequest.RequestAction<Integer>() {
                        @Override
                        public void execute(Callback<Integer> callback, long timeout) {
                            timer.schedule(
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            callback.accept(value);
                                        }
                                    },
                                    delay
                            );
                        }

                        @Override
                        public void cancel() {

                        }
                    }
            );
            simpleRequest.launch(
                    val -> {
                        System.out.println(String.format("Round %s, expect value %s, actual value %s, expect delay %s, actual delay %s", iRef, value, val, delay, System.currentTimeMillis() - now));
                        Assert.assertEquals(value, val.intValue());
                        semaphore.release();
                    }
            );
        }
        semaphore.acquire(total);
        System.out.println(String.format("%s tests accomplished", total));
    }

    @Test
    public void testCancel() throws InterruptedException {
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
        for (int i = 0; i < total; i++) {
            int value = random.nextInt(total);
            int iRef = i;
            long now = System.currentTimeMillis();
            long delay = random.nextInt(5000);
            Request.RequestStatus routine = samples[random.nextInt(samples.length)];
            SimpleRequest<Integer> simpleRequest = new SimpleRequest<>(
                    new SimpleRequest.RequestAction<Integer>() {
                        @Override
                        public void execute(Callback<Integer> callback, long timeout) {
                            timer.schedule(
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            switch (routine) {
                                                case Done:
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
                                                    callback.onException(new RuntimeException());
                                                    break;
                                            }
                                        }
                                    },
                                    delay
                            );
                        }

                        @Override
                        public void cancel() {
                            System.out.println(String.format("Round %s canceled", iRef));
//                            Assert.assertEquals(routine, Request.RequestStatus.Canceled);
                        }
                    }
            );
            switch (routine) {
                case Canceled:
                    timer.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    System.out.println(String.format("Try to cancel round %s", iRef));
                                    simpleRequest.cancel();
                                }
                            },
                            delay / 2
                    );
                    break;
            }
            simpleRequest.launch(
                    new Callback<Integer>() {
                        @Override
                        public void accept(Integer val) {
                            semaphore.release();
                            System.out.println(String.format("Round %s, expect routine %s, actual routine %s", iRef, routine, Request.RequestStatus.Done));
                            Assert.assertEquals(routine, Request.RequestStatus.Done);
                            System.out.println(String.format("Round %s, expect value %s, actual value %s, expect delay %s, actual delay %s", iRef, value, val, delay, System.currentTimeMillis() - now));
                            Assert.assertEquals(value, val.intValue());
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
                    delay + 100L
            );
        }
        while (!semaphore.tryAcquire(total, 10,TimeUnit.SECONDS)){
            System.out.println(String.format("%s tests accomplished at present", semaphore.availablePermits()));
        }
        System.out.println(String.format("%s tests accomplished", total));
    }


}