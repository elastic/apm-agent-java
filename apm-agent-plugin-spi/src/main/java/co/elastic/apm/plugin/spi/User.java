package co.elastic.apm.plugin.spi;

public interface User {
    String getUsername();

    User withUsername(String userName);
}
