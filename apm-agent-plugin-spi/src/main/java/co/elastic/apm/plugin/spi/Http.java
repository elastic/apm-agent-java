package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Http {

    Http withUrl(@Nullable String url);

    Http withMethod(String method);

    Http withStatusCode(int statusCode);

    CharSequence getUrl();

    @Nullable
    String getMethod();
}
