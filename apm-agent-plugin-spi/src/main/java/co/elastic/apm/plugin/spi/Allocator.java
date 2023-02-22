package co.elastic.apm.plugin.spi;

public interface Allocator<T> {

    T createInstance();
}
