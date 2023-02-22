package co.elastic.apm.plugin.spi;

public interface Id {
    boolean isEmpty();

    void setToRandomValue();
}
