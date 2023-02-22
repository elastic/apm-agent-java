package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyHttp implements Http {

    public static final Http INSTANCE = new EmptyHttp();

    private EmptyHttp() {
    }

    @Override
    public Http withUrl(@Nullable String url) {
        return this;
    }

    @Override
    public Http withMethod(String method) {
        return this;
    }

    @Override
    public Http withStatusCode(int statusCode) {
        return this;
    }

    @Nullable
    @Override
    public CharSequence getUrl() {
        return null;
    }

    @Nullable
    @Override
    public String getMethod() {
        return null;
    }
}
