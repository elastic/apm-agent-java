package co.elastic.apm.plugin.spi;

public interface ChildContextCreator<T> {
    boolean asChildOf(TraceContext child, T parent);
}
