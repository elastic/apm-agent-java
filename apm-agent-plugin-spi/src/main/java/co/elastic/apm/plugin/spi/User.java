package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface User {

    @Nullable
    String getUsername();

    User withUsername(String userName);
}
