package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyCloud implements Cloud {

    public static final Cloud INSTANCE = new EmptyCloud();

    private EmptyCloud() {
    }

    @Override
    public Cloud withRegion(@Nullable String region) {
        return this;
    }
}
