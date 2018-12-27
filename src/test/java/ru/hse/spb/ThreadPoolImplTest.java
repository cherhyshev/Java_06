package ru.hse.spb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class ThreadPoolImplTest {
    private volatile int counter;
    private ThreadPoolImpl threadPool;

    @Before
    public void setUp() throws Exception {
        int nthreads = 10;
        threadPool = new ThreadPoolImpl(nthreads);
    }

    @Test(timeout = 1000)
    public void add() throws Exception {
        for (int i = 0; i < 100; i++) {
            threadPool.addRunnable(() -> counter++);
        }
        while (counter != 100) ;
        threadPool.shutdown();
    }

    @Test
    public void shutdown() throws Exception {
        threadPool.shutdown();
    }

    @Test(timeout = 1000)
    public void thenAfterTest() throws Exception {
        final int n = 10;
        LightFuture<Integer> future = null;
        for (int i = 0; i < n; ++i) {
            if (future == null) {
                future = threadPool.addTask(() -> 1);
            } else {
                future = future.thenApply((x) -> x + 1);
            }
        }
        assertEquals((Integer) n, future.get());
    }

}