package ru.hse.spb;

import java.util.function.Supplier;

public interface ThreadPool<R> {

    // Задачи, принятные к исполнению,
    // представлены в виде объектов интерфейса LightFuture
    <R> LightFuture<R>  addTask(Supplier<R> supplier);

    // должен завершить работу потоков
    void shutdown();
}
