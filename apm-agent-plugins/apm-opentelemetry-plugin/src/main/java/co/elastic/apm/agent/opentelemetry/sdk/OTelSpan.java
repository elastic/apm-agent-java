/*
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
 */
package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import co.elastic.apm.agent.impl.transaction.Outcome;
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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OTelSpan implements Span {
    private static final Logger eventLogger = LoggerUtils.logOnce(LoggerFactory.getLogger(OTelSpan.class));

    private final AbstractSpan<?> span;

    public OTelSpan(AbstractSpan<?> span) {
        this.span = span;
        span.incrementReferences();
    }

    @Override
    public <T> Span setAttribute(AttributeKey<T> key, @Nonnull T value) {
        span.getOtelAttributes().put(key.getKey(), value);
        mapAttribute(key, value);
        return this;
    }

    public void mapAttribute(AttributeKey<?> key, Object value) {
        if (span instanceof Transaction) {
            mapTransactionAttributes((Transaction) span, key, value);
        } else {
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
        switch (statusCode) {
            case ERROR:
                span.withOutcome(Outcome.FAILURE);
                break;
            case OK:
                span.withOutcome(Outcome.SUCCESS);
                break;
            case UNSET:
                span.withOutcome(Outcome.UNKNOWN);
                break;
        }
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

        if (OTelSpanKind.CLIENT == span.getOtelKind()) {
            // HTTP client span
            if (key.equals(SemanticAttributes.HTTP_URL) || key.equals(SemanticAttributes.HTTP_SCHEME) || key.getKey().startsWith("http.")) {
                s.withType("external").withSubtype("http");
            }
            if (key.equals(SemanticAttributes.DB_SYSTEM)) {
                s.withType("db").withSubtype((String) value);
            }
        }


        // http.*
        if (mapHttpUrlAttributes(key, value, context.getHttp().getInternalUrl())) {
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

        Map<String, Object> attributes = s.getOtelAttributes();

        String type = null;
        String subType = null;
        StringBuilder destinationResource = s.getContext().getDestination().getService().getResource();

        String netPeerIp = (String) attributes.get("net.peer.ip");
        String netPeerName = (String) attributes.get("net.peer.name");
        Long port = (Long) attributes.get("net.peer.port");
        if (null != port && port < 0) {
            port = null;
        }

        String netPeer = netPeerName != null ? netPeerName : netPeerIp;

        String httpUrl = (String) attributes.get("http.url");
        String httpScheme = (String) attributes.get("http.scheme");
        String dbSystem = (String) attributes.get("db.system");
        String messagingSystem = (String) attributes.get("messaging.system");
        if (null != dbSystem) {
            type = "db";
            subType = dbSystem;
            destinationResource.append(netPeer != null ? netPeer : dbSystem);
        } else if (messagingSystem != null) {
            type = "messaging";
            subType = messagingSystem;
            String messagingDestination = (String) attributes.get("messaging.destination");
            if (messagingDestination != null) {
                destinationResource.append(messagingSystem).append('/').append(messagingDestination);
                port = null; // skip appending port when destination is known
            } else {
                destinationResource.append(netPeer != null ? netPeer : messagingSystem);
            }
        } else if (httpUrl != null || httpScheme != null) {
            type = "external";
            subType = "http";
            s.withType("external").withSubtype("http");

            String httpHost = (String) attributes.get("http.host");
            if (null == httpHost) {
                httpHost = netPeer;
            }
            if (httpHost == null && httpUrl != null) {
                // use HTTP context internal for temp parsing without extra allocation
                Url internalUrl = s.getContext().getHttp().getInternalUrl();
                internalUrl.withFull(httpUrl);
                httpHost = internalUrl.getHostname();
                port = (long) internalUrl.getPort();
                internalUrl.resetState();
            }

            if (httpHost != null) {
                destinationResource.append(httpHost);
            }

            if (port == null) {
                if ("http".equals(httpScheme)) {
                    port = 80L;
                } else if ("https".equals(httpScheme)) {
                    port = 443L;
                }
            }
        } else {
        }

        if (port != null) {
            destinationResource.append(':').append(port);
        }

        s.withType(type).withSubtype(subType);


//        co.elastic.apm.agent.impl.context.SpanContext context = s.getContext();
//        Destination destination = context.getDestination();
//        if (context.getHttp().hasContent()) {
//            s.withType("external").withSubtype("http");
//            Url url = context.getHttp().getInternalUrl();
//            if (context.getDestination().getAddress().length() > 0) {
//                url.withHostname(context.getDestination().getAddress().toString());
//            }
//            if (context.getDestination().getPort() > 0) {
//                url.withPort(context.getDestination().getPort());
//            }
//
//            HttpClientHelper.setDestinationServiceDetails(s, url.getProtocol(), url.getHostname(), url.getPort());
//        } else if (context.getDb().hasContent()) {
//            s.withType("db").withSubtype(context.getDb().getType());
//            if (s.getSubtype() != null) {
//                destination
//                    .getService()
//                    .withName(s.getSubtype())
//                    .withResource(s.getSubtype())
//                    .withType("db");
//            }
////        } else {
////            s.withType("app");
////            if (destination.getService().hasContent()) {
////                destination.getService().withType("app");
////            }
//        }
    }

    private void getResource() {

    }

    /**
     * Only one of the following is required per OpenTelemetry's semantic conventions:
     * <p>
     * Client:
     * - http.url
     * - http.scheme, http.host, http.target
     * - http.scheme, net.peer.name, net.peer.port, http.target
     * - http.scheme, net.peer.ip, net.peer.port, http.target
     * <p>
     * Server:
     * - http.url
     * - http.scheme, http.host, http.target
     * - http.scheme, http.server_name, net.host.port, http.target
     * - http.scheme, net.host.name, net.host.port, http.target
     * <p>
     * The net.* fields are captured on span/transaction end because by the time they are set,
     * we don't necessarily know whether the span represents an http operation
     */
    private boolean mapHttpUrlAttributes(AttributeKey<?> key, Object value, Url url) {
        if (key.equals(SemanticAttributes.HTTP_URL)) {
            url.withFull((String) value);
            // ensure other fields or URL are populated through parsing
            url.parseAndFillFromFull();
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
        if (url.getHostname() != null && url.getPort() > 0) {
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
                if (url.getPort() <= 0) {
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
        return new OTelSpanContext(span.getTraceContext());
    }

    @Override
    public boolean isRecording() {
        return span.isSampled();
    }

    public AbstractSpan<?> getInternalSpan() {
        return span;
    }

    @Override
    public String toString() {
        return "OtelSpan[" + span + "]";
    }
}
