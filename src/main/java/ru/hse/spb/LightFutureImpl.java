package ru.hse.spb;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class LightFutureImpl<R> implements LightFuture<R> {
    private final ThreadPoolImpl threadPool;
    private volatile boolean ready;
    private volatile R result;
    private volatile Throwable throwable;
    private Collection<Runnable> taskChildren;

    public <T> LightFutureImpl(ThreadPoolImpl threadPool, Callable<R> callable, LightFutureImpl<T> parent) {
        this.threadPool = threadPool;
        ready = false;
        taskChildren = new ArrayList<>();
        Runnable runnable = getRunnable(callable);
        if (parent == null) {
            threadPool.addRunnable(runnable);
        } else {
            synchronized (parent) {
                if (parent.isReady()) {
                    threadPool.addRunnable(runnable);
                } else {
                    parent.taskChildren.add(runnable);
                }
            }
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public R get() throws LightExecutionException {
        while (true) {
            synchronized (this) {
                if (ready) {
                    return getResult();
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public <T> LightFuture<T> thenApply(Function<? super R, ? extends T> function) {
        return new LightFutureImpl<>(threadPool, () -> function.apply(get()), this);
    }

    private Runnable getRunnable(Callable<R> callable) {
        return () -> {
            try {
                setResult(callable.call());
            } catch (Throwable t) {
                setException(t);
            } finally {
                setReady();
            }
            synchronized (this) {
                notifyAll();
                for (Runnable runnable : taskChildren) {
                    threadPool.addRunnable(runnable);
                }
                taskChildren = null;
            }
        };
    }

    private void setResult(R result) {
        this.result = result;
    }

    private void setReady() {
        ready = true;
    }

    private void setException(Throwable throwable) {
        assert this.throwable == null;
        this.throwable = throwable;
    }

    private R getResult() throws LightExecutionException {
        if (throwable != null) {
            throw new LightExecutionException();
        }
        return result;
    }
}
