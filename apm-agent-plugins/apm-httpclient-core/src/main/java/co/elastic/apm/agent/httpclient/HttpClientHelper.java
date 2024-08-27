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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.metadata.BodyCapture;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpClientHelper {

    private static final Pattern CHARSET_EXTRACTOR = Pattern.compile(";\\s*charset\\s*=\\s*((\"[^\"]+)|([^;\\s]+))");

    private static final Logger logger = LoggerFactory.getLogger(HttpClientHelper.class);

    public static final String EXTERNAL_TYPE = "external";
    public static final String HTTP_SUBTYPE = "http";

    @Nullable
    public static Span<?> startHttpClientSpan(TraceState<?> activeContext, String method, @Nullable URI uri, @Nullable CharSequence hostName) {
        String uriString = null;
        String scheme = null;
        int port = -1;
        if (uri != null) {
            uriString = uri.toString();
            scheme = uri.getScheme();
            port = uri.getPort();
            if (hostName == null) {
                hostName = uri.getHost();
            }
        }
        return startHttpClientSpan(activeContext, method, uriString, scheme, hostName, port);
    }

    @Nullable
    public static Span<?> startHttpClientSpan(TraceState<?> activeContext, String method, @Nullable String uri,
                                              @Nullable String scheme, @Nullable CharSequence hostName, int port) {
        Span<?> span = activeContext.createExitSpan();
        if (span != null) {
            if (span.isSampled()) {
                span.getContext().getHttp().getRequestBody().markEligibleForCapturing();
            }
            updateHttpSpanNameAndContext(span, method, uri, scheme, hostName, port);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Created an HTTP exit span: {} for URI: {}. Parent span: {}", span, uri, activeContext);
        }
        return span;
    }

    public static <R> void checkBodyCapturePreconditions(@Nullable AbstractSpan<?> abstractSpan, R request, TextHeaderGetter<R> headerGetter) {
        if (!(abstractSpan instanceof Span<?>)) {
            return;
        }
        Span<?> span = (Span<?>) abstractSpan;
        BodyCapture bodyCapture = span.getContext().getHttp().getRequestBody();
        if (!bodyCapture.isEligibleForCapturing()) {
            return;
        }
        if (bodyCapture.havePreconditionsBeenChecked()) {
            return;
        }
        WebConfiguration webConfig = GlobalTracer.get().getConfig(WebConfiguration.class);
        int byteCount = webConfig.getCaptureClientRequestBytes();
        if (byteCount == 0) {
            bodyCapture.markPreconditionsFailed();
            return;
        }
        List<WildcardMatcher> contentTypes = webConfig.getCaptureContentTypes();
        String contentTypeHeader = headerGetter.getFirstHeader("Content-Type", request);
        if (contentTypeHeader == null) {
            contentTypeHeader = "";
        }
        if (WildcardMatcher.anyMatch(contentTypes, contentTypeHeader) == null) {
            bodyCapture.markPreconditionsFailed();
            return;
        }
        bodyCapture.markPreconditionsPassed(extractCharsetFromContentType(contentTypeHeader), byteCount);
    }

    public static <R> boolean checkAndStartRequestBodyCapture(@Nullable AbstractSpan<?> abstractSpan, R request, TextHeaderGetter<R> headerGetter) {
        if (!(abstractSpan instanceof Span<?>)) {
            return false;
        }
        checkBodyCapturePreconditions(abstractSpan, request, headerGetter);
        Span<?> span = (Span<?>) abstractSpan;
        return span.getContext().getHttp().getRequestBody().startCapture();
    }

    //Visible for testing
    @Nullable
    static String extractCharsetFromContentType(String contentTypeHeader) {
        Matcher matcher = CHARSET_EXTRACTOR.matcher(contentTypeHeader);
        if (matcher.find()) {
            String potentiallyQuotedCharset = matcher.group(1);
            return potentiallyQuotedCharset.replace("\"", "");
        }
        return null;
    }

    public static void updateHttpSpanNameAndContext(Span<?> span, String method, @Nullable String uri, String scheme, CharSequence hostName, int port) {
        span.withType(EXTERNAL_TYPE)
            .withSubtype(HTTP_SUBTYPE)
            .withName(method).appendToName(" ").appendToName(hostName != null ? hostName : "unknown host");

        span.getContext().getHttp()
            .withUrl(uri)
            .withMethod(method);

        setDestinationServiceDetails(span, scheme, hostName, port);
    }

    public static void setDestinationServiceDetails(Span<?> span, @Nullable String scheme, @Nullable CharSequence host, int port) {
        if (scheme == null || host == null || host.length() == 0) {
            return;
        }

        if ("http".equals(scheme)) {
            if (port < 0) {
                port = 80;
            }
        } else if ("https".equals(scheme)) {
            if (port < 0) {
                port = 443;
            }
        } else {
            return;
        }

        span.getContext().getDestination()
            .withAddress(host)
            .withPort(port);

        span.getContext().getServiceTarget()
            .withType("http")
            .withHostPortName(host, port)
            .withNameOnlyDestinationResource();

    }
}
