/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ElasticOTelSpan implements Span {
    private final AbstractSpan<?> span;

    public ElasticOTelSpan(AbstractSpan<?> span) {
        this.span = span;
        span.incrementReferences();
    }

    @Override
    public <T> Span setAttribute(AttributeKey<T> key, @Nonnull T value) {
        mapAttribute(key, value);
        return this;
    }

    public void mapAttribute(AttributeKey<?> key, Object value) {
        if (span instanceof Transaction) {
            mapTransactionAttributes((Transaction) span, key, value);
        } else if (span instanceof Span) {
            mapSpanAttributes((co.elastic.apm.agent.impl.transaction.Span) span, key, value);
        }
    }

    @Override
    public Span addEvent(String name, Attributes attributes) {
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
        return this;
    }

    @Override
    public Span setStatus(StatusCode statusCode, String description) {
        if (span instanceof Transaction) {
            Transaction t = (Transaction) span;
            switch (statusCode) {
                case OK:
                    t.withResultIfUnset("OK");
                    break;
                case ERROR:
                    t.withResultIfUnset("Error");
                    break;
            }
        }
        // TODO set outcome
        return this;
    }

    @Override
    public Span recordException(Throwable exception, Attributes additionalAttributes) {
        span.captureException(exception);
        return this;
    }

    @Override
    public Span updateName(String name) {
        span.withName(name);
        return this;
    }

    @Override
    public void end() {
        if (span instanceof Transaction) {
            onTransactionEnd();
        } else if (span instanceof Span) {
            onSpanEnd();
        }
        span.end();
    }

    private void mapTransactionAttributes(Transaction t, AttributeKey<?> key, Object value) {
        Request request = t.getContext().getRequest();
        // http.*
        if (key.equals(SemanticAttributes.HTTP_STATUS_CODE)) {
            t.getContext().getResponse().withStatusCode(((Number) value).intValue());
            t.withResult(ResultUtil.getResultByHttpStatus(((Number) value).intValue()));
        } else if (key.equals(SemanticAttributes.HTTP_URL)) {
            StringBuilder fullURl = request.getUrl().getFull();
            fullURl.setLength(0);
            fullURl.append((String) value);
            try {
                URL url = new URL((String) value);
                request.getUrl().withSearch(url.getQuery());
                request.getUrl().withProtocol(url.getProtocol());
                request.getUrl().withPathname(url.getPath());
                request.getUrl().withHostname(url.getHost());
                int port = url.getPort();
                port = port > 0 ? port : url.getDefaultPort();
                if (port > 0) {
                    request.getUrl().withPort(port);
                }
            } catch (MalformedURLException ignore) {
            }
        } else if (key.equals(SemanticAttributes.HTTP_TARGET)) {
            StringBuilder fullURl = request.getUrl().getFull();
            if (fullURl.length() == 0) {
                fullURl.append((String) value);
            }
        } else if (key.equals(SemanticAttributes.HTTP_METHOD)) {
            request.withMethod((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_HOST)) {
            String httpHost = (String) value;
            int indexOfColon = httpHost.indexOf(':');
            if (indexOfColon > 0) {
                request.getUrl().withHostname(httpHost.substring(0, indexOfColon));
                try {
                    request.getUrl().withPort(Integer.parseInt(httpHost.substring(indexOfColon + 1)));
                } catch (NumberFormatException ignore) {
                }
            } else {
                request.getUrl().withHostname(httpHost);
            }
        } else if (key.equals(SemanticAttributes.HTTP_SERVER_NAME)) {
            request.getUrl().withHostname((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_SCHEME)) {
            request.getUrl().withProtocol((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_ROUTE)) {
            request.getUrl().withPathname((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_FLAVOR)) {
            request.withHttpVersion((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_CLIENT_IP)) {
            request.getHeaders().add("X-Forwarded-For", (String) value);
        } else if (key.equals(SemanticAttributes.HTTP_USER_AGENT)) {
            request.getHeaders().add("User-Agent", (String) value);
        } else {
            setAttributeAsLabel(t, key, value);
        }

    }

    private void onTransactionEnd() {
        Transaction t = (Transaction) span;
        Request request = t.getContext().getRequest();
        if (request.hasContent()) {
            t.withType("request");
            Url requestUrl = request.getUrl();
            if (requestUrl.getProtocol() == null) {
                requestUrl.withProtocol("http");
            }
            Iterator<? extends Map.Entry<String, ?>> iterator = span.getContext().getLabelIterator();
            while (iterator.hasNext()) {
                Map.Entry<String, ?> entry = iterator.next();
                // net.*
                if (entry.getKey().equals(SemanticAttributes.NET_HOST_NAME.getKey())) {
                    if (requestUrl.getHostname() == null) {
                        requestUrl.withHostname((String) entry.getValue());
                    }
                } else if (entry.getKey().equals(SemanticAttributes.NET_HOST_PORT.getKey())) {
                    if (requestUrl.getPort().length() == 0) {
                        requestUrl.withPort(((Number) entry.getValue()).intValue());
                    }
                } else if (entry.getKey().equals(SemanticAttributes.NET_PEER_IP.getKey())) {
                    String remoteAddress = request.getSocket().getRemoteAddress();
                    request.getSocket().withRemoteAddress(entry.getValue() + (remoteAddress == null ? "" : remoteAddress));
                } else if (entry.getKey().equals(SemanticAttributes.NET_PEER_PORT.getKey())) {
                    String remoteAddress = request.getSocket().getRemoteAddress();
                    request.getSocket().withRemoteAddress((remoteAddress == null ? "" : remoteAddress) + ":" + entry.getValue());
                }
            }
            // if the URL starts with / we have only captured the http.target and have to construct the full url
            if (requestUrl.getFull().length() > 0 && requestUrl.getFull().charAt(0) == '/') {
                String httpTarget = requestUrl.getFull().toString();
                requestUrl.getFull().setLength(0);
                requestUrl
                    .appendToFull(requestUrl.getProtocol())
                    .appendToFull("://")
                    .appendToFull(requestUrl.getHostname())
                    .appendToFull(requestUrl.getPort().length() > 0 ? ":" + requestUrl.getPort() : "")
                    .appendToFull(httpTarget);
            }

        } else {
            t.withType("unknown");
        }
        t.setFrameworkName("OpenTelemetry");
        t.setFrameworkVersion(VersionUtils.getVersion(OpenTelemetry.class, "io.opentelemetry", "opentelemetry-api"));

    }

    private void mapSpanAttributes(co.elastic.apm.agent.impl.transaction.Span s, AttributeKey<?> key, Object value) {
        co.elastic.apm.agent.impl.context.SpanContext context = s.getContext();

        // http.*
        if (key.equals(SemanticAttributes.HTTP_STATUS_CODE)) {
            context.getHttp().withStatusCode(((Number) value).intValue());
        } else if (key.equals(SemanticAttributes.HTTP_URL)) {
            context.getHttp().withUrl((String) value);
        }  else if (key.equals(SemanticAttributes.HTTP_TARGET)) {
            if (context.getHttp().getUrl() == null) {
                context.getHttp().withUrl((String) value);
            }
        } else if (key.equals(SemanticAttributes.HTTP_METHOD)) {
            context.getHttp().withMethod((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_HOST)) {
            context.getDestination().withAddressPort((String) value);
        }
        // net.*
        else if (key.equals(SemanticAttributes.NET_PEER_NAME)) {
            context.getDestination().withAddress((String) value);
        } else if (key.equals(SemanticAttributes.NET_PEER_IP)) {
            if (context.getDestination().getAddress().length() == 0) {
                context.getDestination().withAddress((String) value);
            }
        } else if (key.equals(SemanticAttributes.NET_PEER_PORT)) {
            context.getDestination().withPort(((Number) value).intValue());
        }
        // db.*
        else if (key.equals(SemanticAttributes.DB_SYSTEM)) {
            s.withType((String) value);
        } else if (key.equals(SemanticAttributes.DB_NAME)) {
            context.getDb().withInstance((String) value);
        } else if (key.equals(SemanticAttributes.DB_STATEMENT)) {
            context.getDb().withStatement((String) value);
        } else if (key.equals(SemanticAttributes.DB_USER)) {
            context.getDb().withUser((String) value);
        } else {
            setAttributeAsLabel(s, key, value);
        }
    }

    private void onSpanEnd() {
        co.elastic.apm.agent.impl.transaction.Span s = (co.elastic.apm.agent.impl.transaction.Span) this.span;
        co.elastic.apm.agent.impl.context.SpanContext context = s.getContext();
        if (context.getHttp().hasContent()) {
            s.withType("external").withSubtype("http");
        } else if (context.getDb().hasContent()) {
            s.withType("db").withSubtype(context.getDb().getType());
            if (s.getSubtype() != null) {
                context.getDestination()
                    .getService()
                    .withName(s.getSubtype())
                    .withType(s.getSubtype());
            }
        } else {
            s.withType("app");
        }
        if (context.getDestination().getService().hasContent()) {
            context.getDestination().getService().withType(s.getType());
        }
    }

    private static void setAttributeAsLabel(AbstractSpan<?> span, AttributeKey<?> key, Object value) {
        if (value instanceof Boolean) {
            span.addLabel(key.getKey(), (Boolean) value);
        } else if (value instanceof Number) {
            span.addLabel(key.getKey(), (Number) value);
        } else {
            span.addLabel(key.getKey(), value.toString());
        }
    }

    @Override
    public void end(long timestamp, TimeUnit unit) {
        span.end(unit.toMicros(timestamp));
    }

    @Override
    public SpanContext getSpanContext() {
        return new ElasticOTelSpanContext(span.getTraceContext());
    }

    @Override
    public boolean isRecording() {
        return span.isSampled();
    }

    public AbstractSpan<?> getInternalSpan() {
        return span;
    }
}
