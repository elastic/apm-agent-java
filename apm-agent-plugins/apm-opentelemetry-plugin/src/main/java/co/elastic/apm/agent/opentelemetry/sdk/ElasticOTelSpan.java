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

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.apm.agent.util.VersionUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ElasticOTelSpan implements Span {
    private static final Logger eventLogger = LoggerUtils.logOnce(LoggerFactory.getLogger(ElasticOTelSpan.class));

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
        } else if (span instanceof co.elastic.apm.agent.impl.transaction.Span) {
            mapSpanAttributes((co.elastic.apm.agent.impl.transaction.Span) span, key, value);
        }
    }

    @Override
    public Span addEvent(String name, Attributes attributes) {
        eventLogger.warn("The addEvent API is not supported at the moment");
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
        eventLogger.warn("The addEvent API is not supported at the moment");
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
            onTransactionEnd((Transaction) span);
        } else if (span instanceof co.elastic.apm.agent.impl.transaction.Span) {
            onSpanEnd((co.elastic.apm.agent.impl.transaction.Span) span);
        }
        span.end();
    }

    private void mapTransactionAttributes(Transaction t, AttributeKey<?> key, Object value) {
        Request request = t.getContext().getRequest();
        Url url = request.getUrl();
        // http.*
        if (key.equals(SemanticAttributes.HTTP_STATUS_CODE)) {
            t.getContext().getResponse().withStatusCode(((Number) value).intValue());
            t.withResult(ResultUtil.getResultByHttpStatus(((Number) value).intValue()));
        } else if (mapHttpUrlAttributes(key, value, url)) {
            // successfully mapped inside mapHttpUrlAttributes
        } else if (key.equals(SemanticAttributes.HTTP_METHOD)) {
            request.withMethod((String) value);
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

    private void onTransactionEnd(Transaction t) {
        Request request = t.getContext().getRequest();
        if (request.hasContent()) {
            t.withType("request");
            Url url = request.getUrl();
            captureNetHostUrlAttributes(url, span.getContext());
            request.getSocket().withRemoteAddress(getClientRemoteAddress(span.getContext()));
            if (url.hasContent()) {
                StringBuilder fullUrl = url.getFull();
                if (fullUrl.length() > 0) {
                    try {
                        url.fillFromFullUrl(new URL(fullUrl.toString()));
                    } catch (MalformedURLException ignore) {
                    }
                } else {
                    url.fillFullUrl();
                }
            }
        } else {
            t.withType("unknown");
        }
        t.setFrameworkName("OpenTelemetry");
        t.setFrameworkVersion(VersionUtils.getVersion(OpenTelemetry.class, "io.opentelemetry", "opentelemetry-api"));

    }

    @Nullable
    public String getClientRemoteAddress(AbstractContext context) {
        String netPeerIp = null;
        Long netPeerPort = null;
        Iterator<? extends Map.Entry<String, ?>> iterator = context.getLabelIterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ?> entry = iterator.next();
            // net.*
            if (entry.getKey().equals(SemanticAttributes.NET_PEER_IP.getKey())) {
                netPeerIp = (String) entry.getValue();
            } else if (entry.getKey().equals(SemanticAttributes.NET_PEER_PORT.getKey())) {
                netPeerPort = (Long) entry.getValue();
            }
        }
        if (netPeerIp != null && netPeerPort != null) {
            return netPeerIp + ":" + netPeerPort;
        }
        return null;
    }

    private void mapSpanAttributes(co.elastic.apm.agent.impl.transaction.Span s, AttributeKey<?> key, Object value) {
        co.elastic.apm.agent.impl.context.SpanContext context = s.getContext();

        // http.*
        if (mapHttpUrlAttributes(key, value, context.getHttp().getUrl())) {
            // successfully mapped inside mapHttpUrlAttributes
        } else if (key.equals(SemanticAttributes.HTTP_STATUS_CODE)) {
            context.getHttp().withStatusCode(((Number) value).intValue());
        } else if (key.equals(SemanticAttributes.HTTP_METHOD)) {
            context.getHttp().withMethod((String) value);
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

    private void onSpanEnd(co.elastic.apm.agent.impl.transaction.Span s) {
        co.elastic.apm.agent.impl.context.SpanContext context = s.getContext();
        Destination destination = context.getDestination();
        if (context.getHttp().hasContent()) {
            s.withType("external").withSubtype("http");
            Url url = context.getHttp().getUrl();
            if (context.getDestination().getAddress().length() > 0) {
                url.withHostname(context.getDestination().getAddress().toString());
            }
            if (context.getDestination().getPort() > 0) {
                url.withPort(context.getDestination().getPort());
            }
            // The full url is the only thing we report on spans.
            // Instrumentations may not set the full url but only it's components (see mapHttpUrlAttributes)
            url.fillFullUrl();
            if (url.getProtocol() == null || url.getHostname() == null) {
                try {
                    // We also need the different pieces of the url in order to determine the destination details
                    url.fillFromFullUrl(new URL(url.getFull().toString()));
                } catch (MalformedURLException ignore) {
                }
            }
            HttpClientHelper.setDestinationServiceDetails(s, url.getProtocol(), url.getHostname(), url.getPortAsInt());
        } else if (context.getDb().hasContent()) {
            s.withType("db").withSubtype(context.getDb().getType());
            if (s.getSubtype() != null) {
                destination
                    .getService()
                    .withName(s.getSubtype())
                    .withResource(s.getSubtype())
                    .withType("db");
            }
        } else {
            s.withType("app");
            if (destination.getService().hasContent()) {
                destination.getService().withType("app");
            }
        }
    }

    /**
     * Only one of the following is required per OpenTelemetry's semantic conventions:
     *
     * Client:
     *   - http.url
     *   - http.scheme, http.host, http.target
     *   - http.scheme, net.peer.name, net.peer.port, http.target
     *   - http.scheme, net.peer.ip, net.peer.port, http.target
     *
     * Server:
     *   - http.url
     *   - http.scheme, http.host, http.target
     *   - http.scheme, http.server_name, net.host.port, http.target
     *   - http.scheme, net.host.name, net.host.port, http.target
     *
     * The net.* fields are captured on span/transaction end because by the time they are set,
     * we don't necessarily know whether the span represents an http operation
     */
    private boolean mapHttpUrlAttributes(AttributeKey<?> key, Object value, Url url) {
        if (key.equals(SemanticAttributes.HTTP_URL)) {
            StringBuilder fullURl = url.getFull();
            fullURl.setLength(0);
            fullURl.append((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_TARGET)) {
            String httpTarget = (String) value;
            int indexOfQuery = httpTarget.indexOf('?');
            if (indexOfQuery > 0) {
                url.withPathname(httpTarget.substring(0, indexOfQuery));
                url.withSearch(httpTarget.substring(Math.min(indexOfQuery + 1, httpTarget.length())));
            }
        } else if (key.equals(SemanticAttributes.HTTP_HOST)) {
            String httpHost = (String) value;
            int indexOfColon = httpHost.indexOf(':');
            if (indexOfColon > 0) {
                url.withHostname(httpHost.substring(0, indexOfColon));
                try {
                    url.withPort(Integer.parseInt(httpHost.substring(indexOfColon + 1)));
                } catch (NumberFormatException ignore) {
                }
            } else {
                url.withHostname(httpHost);
            }
        } else if (key.equals(SemanticAttributes.HTTP_SERVER_NAME)) {
            url.withHostname((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_SCHEME)) {
            url.withProtocol((String) value);
        } else if (key.equals(SemanticAttributes.HTTP_ROUTE)) {
            url.withPathname((String) value);
        } else {
            return false;
        }
        return true;
    }

    /**
     * these properties may have been set before we know it's an http request, that's why this capture is called on span end
     */
    private void captureNetHostUrlAttributes(Url url, AbstractContext context) {
        if (url.getHostname() != null && url.getPort().length() > 0) {
            return;
        }
        Iterator<? extends Map.Entry<String, ?>> iterator = context.getLabelIterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ?> entry = iterator.next();
            // net.*
            if (entry.getKey().equals(SemanticAttributes.NET_HOST_NAME.getKey())) {
                if (url.getHostname() == null) {
                    url.withHostname((String) entry.getValue());
                }
            } else if (entry.getKey().equals(SemanticAttributes.NET_HOST_IP.getKey())) {
                if (url.getHostname() == null) {
                    url.withHostname((String) entry.getValue());
                }
            } else if (entry.getKey().equals(SemanticAttributes.NET_HOST_PORT.getKey())) {
                if (url.getPort().length() == 0) {
                    url.withPort(((Number) entry.getValue()).intValue());
                }
            }
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
