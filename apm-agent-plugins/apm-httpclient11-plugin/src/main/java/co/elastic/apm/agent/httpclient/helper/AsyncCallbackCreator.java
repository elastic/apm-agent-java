package co.elastic.apm.agent.httpclient.helper;

import co.elastic.apm.agent.impl.transaction.Span;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public interface AsyncCallbackCreator {

    BiConsumer<HttpResponse, Throwable> create(Span span);
}
