package ru.hse.spb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class LightFutureImpl<R> implements LightFuture<R> {
    private final ThreadPoolImpl threadPool;
    private LightFutureImpl parent;
    private boolean ready;
    private R result;
    private Throwable throwable;
    private List<Runnable> taskChildren;

    public <T> LightFutureImpl(ThreadPoolImpl threadPool,
                               Callable<R> callable,
                               LightFutureImpl<T> parent) {

        this.threadPool = threadPool;
        this.parent = parent;
        ready = false;
        taskChildren = new ArrayList<>();
        Runnable runnable = getRunnable(callable);
        handleParentFuture(runnable);
    }

    private synchronized void handleParentFuture(Runnable runnable) {
        if (parent == null || parent.isReady()) {
            threadPool.addRunnable(runnable);
        } else {
            parent.taskChildren.add(runnable);
        }
    }

    @Override
    public synchronized boolean isReady() {
        return ready;
    }

    @Override
    public synchronized R get() throws LightExecutionException {
        while (true) {
            if (isReady()) {
                synchronized (this) {
                    if (isReady()) {
                        return getResult();
                    }
                }
            }
            try {
                wait();
            } catch (InterruptedException e) {
                throw new LightExecutionException(e.getMessage());
            }
        }
    }

    @Override
    public <T> LightFuture<T> thenApply(Function<? super R, ? extends T> function) {
        while (true) {
            if (isReady()) {
                synchronized (this) {
                    if (isReady()) {
                        return new LightFutureImpl<>(threadPool, () -> function.apply(get()), this);
                    }
                }
            }
            Thread.yield();
        }
    }

    private Runnable getRunnable(Callable<R> callable) {
        return () -> {
            try {
                setResult(callable.call());
            } catch (Throwable t) {
                setException(t);
            } finally {
                makeReady();
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

    private synchronized void setResult(R result) {
        this.result = result;
    }

    private synchronized void makeReady() {
        ready = true;
    }

    private synchronized void setException(Throwable throwable) {
        assert this.throwable == null;
        this.throwable = throwable;
    }

    private synchronized R getResult() throws LightExecutionException {
        if (throwable != null) {
            throw new LightExecutionException(throwable.getMessage());
        }
        return result;
    }
}
