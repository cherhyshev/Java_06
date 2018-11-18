package ru.hse.spb;

import java.util.function.Function;
import java.util.function.Supplier;

public interface LightFuture<R> {
    // возвращает true, если задача выполнена
    boolean isReady();

    // * В случае, если соответствующий задаче supplier завершился с исключением,
    // этот метод должен завершиться с исключением LightExecutionException
    //
    // * Если результат еще не вычислен, метод ожидает его
    // и возвращает полученное значение
    R get() throws LightExecutionException;

    // принимает объект типа Function, который может быть применен к результату
    // данной задачи X и возвращает новую задачу Y, принятую к исполнению
    //
    // * Новая задача будет исполнена не ранее, чем завершится исходная
    //
    // * В качестве аргумента объекту Function будет передан результат
    // исходной задачи, и все Y должны исполняться на общих основаниях
    // (т.е. должны разделяться между потоками пула)

    // * Метод thenApply может быть вызван несколько раз
    <T> LightFuture<T> thenApply(Function<? super R, ? extends T> function);

}
