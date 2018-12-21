package ru.hse.spb;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;

public class ThreadPoolImpl<R> implements ThreadPool<R> {
    private final Queue<Thread> threadQueue;
    private final Queue<Runnable> runnableQueue;

    public ThreadPoolImpl(int threadNum) {
        threadQueue = new ArrayDeque<>(threadNum);
        runnableQueue = new ArrayDeque<>();
        for (int i = 0; i < threadNum; i++) {

            threadQueue.add(new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Runnable nextTask = null;
                    synchronized (runnableQueue) {
                        if (!runnableQueue.isEmpty()) {
                            nextTask = runnableQueue.poll();
                        }
                        if (nextTask != null) {
                            nextTask.run();
                        }
                        try {
                            if (runnableQueue.isEmpty()) {
                                runnableQueue.wait();
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }));
        }

        for (Thread tr : threadQueue) {
            tr.start();
        }

    }

    void addRunnable(Runnable runnable) {
        synchronized (runnableQueue) {
            runnableQueue.add(runnable);
            runnableQueue.notify();
        }
    }


    @Override
    public <R> LightFuture<R> addTask(Supplier<R> task) {
        return new LightFutureImpl<R>(this, task::get, null);

    }

    @Override
    public void shutdown() {
        for (Thread thread : threadQueue) {
            thread.interrupt();
        }

    }
}
