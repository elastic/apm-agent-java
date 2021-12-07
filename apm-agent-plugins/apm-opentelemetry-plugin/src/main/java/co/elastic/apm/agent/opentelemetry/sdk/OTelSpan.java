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

import co.elastic.apm.agent.impl.context.Url;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
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
        return this;
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
                span.withUserOutcome(Outcome.FAILURE);
                break;
            case OK:
                span.withUserOutcome(Outcome.SUCCESS);
                break;
            case UNSET:
                span.withUserOutcome(Outcome.UNKNOWN);
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


    private void onTransactionEnd(Transaction t) {

        Map<String, Object> attributes = span.getOtelAttributes();
        boolean isRpc = attributes.containsKey("rpc.system");
        boolean isHttp = attributes.containsKey("http.url") || attributes.containsKey("http.scheme");
        boolean isMessaging = attributes.containsKey("messaging.system");
        String type = "unknown";
        if (span.getOtelKind() == OTelSpanKind.SERVER && (isRpc || isHttp)) {
            type = "request";
        } else if (span.getOtelKind() == OTelSpanKind.CONSUMER && isMessaging) {
            type = "messaging";
        }

        t.withType(type);

        t.setFrameworkName("OpenTelemetry");
        t.setFrameworkVersion(VersionUtils.getVersion(OpenTelemetry.class, "io.opentelemetry", "opentelemetry-api"));
    }

    private void onSpanEnd(co.elastic.apm.agent.impl.transaction.Span s) {

        Map<String, Object> attributes = s.getOtelAttributes();

        String type = null;
        String subType = null;
        StringBuilder destinationResource = s.getContext().getDestination().getService().getResource();

        String netPeerIp = (String) attributes.get("net.peer.ip");
        String netPeerName = (String) attributes.get("net.peer.name");
        Long netPortLong = (Long) attributes.get("net.peer.port");
        int netPort = -1;
        if (null != netPortLong && netPortLong > 0L) {
            netPort = netPortLong.intValue();
        }

        String netPeer = netPeerName != null ? netPeerName : netPeerIp;

        String httpUrl = (String) attributes.get("http.url");
        String httpScheme = (String) attributes.get("http.scheme");
        String dbSystem = (String) attributes.get("db.system");
        String messagingSystem = (String) attributes.get("messaging.system");
        String rpcSystem = (String) attributes.get("rpc.system");
        if (null != dbSystem) {
            type = "db";
            subType = dbSystem;
            String dbName = (String) attributes.get("db.name");
            setSpanResource(destinationResource, netPeer, netPort, dbSystem, dbName);
        } else if (messagingSystem != null) {
            type = "messaging";
            subType = messagingSystem;
            String messagingDestination = (String) attributes.get("messaging.destination");
            URI messagingUri = parseURI((String) attributes.get("messaging.url"));

            if (netPeer == null && messagingUri != null) {
                netPeer = messagingUri.getHost();
                netPort = messagingUri.getPort();
            }
            setSpanResource(destinationResource, netPeer, netPort, messagingSystem, messagingDestination);
        } else if (rpcSystem != null) {
            type = "external";
            subType = rpcSystem;
            String service = (String) attributes.get("rpc.service");

            setSpanResource(destinationResource, netPeer, netPort, rpcSystem, service);

        } else if (httpUrl != null || httpScheme != null) {
            type = "external";
            subType = "http";

            String httpHost = (String) attributes.get("http.host");
            if (null == httpHost) {
                httpHost = netPeer;
            }
            if (httpHost == null && httpUrl != null) {
                URI httpUri = parseURI(httpUrl);
                if (httpUri != null) {
                    httpHost = httpUri.getHost();
                    netPort = httpUri.getPort();
                    httpScheme = httpUri.getScheme();
                }
            }

            netPort = Url.normalizePort(netPort, httpScheme);

            setSpanResource(destinationResource, httpHost, netPort, null, null);
        }

        if (type == null) {
            type = "unknown";
            if (s.getOtelKind() == OTelSpanKind.INTERNAL) {
                type = "app";
                subType = "internal";
            }
        }

        s.withType(type).withSubtype(subType);
    }

    @Nullable
    private static URI parseURI(@Nullable String s) {
        if (null == s) {
            return null;
        }
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static void setSpanResource(StringBuilder resource,
                                        @Nullable String netPeer,
                                        int netPort,
                                        @Nullable String system,
                                        @Nullable String suffix) {

        boolean allowSuffix = false;
        if (netPeer == null && system != null) {
            resource.append(system);
            allowSuffix = true;
        } else if (netPeer != null) {
            resource.append(netPeer);
            allowSuffix = true;
            if (netPort > 0) {
                resource.append(':').append(netPort);
            }
        }

        if (allowSuffix && suffix != null) {
            resource.append('/').append(suffix);
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
