package ru.hse.spb;

import java.util.*;
import java.util.function.Supplier;

public class ThreadPoolImpl<R> implements ThreadPool<R> {
    private final List<Thread> threadList;
    private final List<Runnable> taskList;

    public ThreadPoolImpl(int threadNum) {
        threadList = new ArrayList<>(threadNum);
        taskList = new LinkedList<>();
        for (int i = 0; i < threadNum; i++) {

            threadList.add(new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Runnable nextTask = null;
                    synchronized (taskList) {
                        if (!taskList.isEmpty()) {
                            nextTask = taskList.remove(0);
                        }
                    }
                    if (nextTask != null) {
                        nextTask.run();
                    }
                    synchronized (taskList) {
                        try {
                            if (taskList.isEmpty()) {
                                taskList.wait();
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }));
        }

        for (Thread tr : threadList) {
            tr.start();
        }

    }

    void addRunnable(Runnable runnable) {
        synchronized (taskList) {
            taskList.add(runnable);
            taskList.notify();
        }
    }


    @Override
    public <R> LightFuture<R> addTask(Supplier<R> task) {
        return new LightFutureImpl<R>(this, task::get, null);

    }

    @Override
    public void shutdown() {
        for (Thread thread : threadList) {
            thread.interrupt();
        }

    }
}
