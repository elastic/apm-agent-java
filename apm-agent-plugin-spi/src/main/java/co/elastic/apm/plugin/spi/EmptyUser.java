package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyUser implements User {

    public static final User INSTANCE = new EmptyUser();

    private EmptyUser() {
    }

    @Nullable
    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public User withUsername(String userName) {
        return this;
    }
}
