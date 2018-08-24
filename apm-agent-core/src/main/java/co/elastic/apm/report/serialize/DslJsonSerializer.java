/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.report.serialize;

import co.elastic.apm.impl.MetaData;
import co.elastic.apm.impl.context.TransactionContext;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.context.Socket;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.impl.context.User;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.error.ErrorPayload;
import co.elastic.apm.impl.payload.Agent;
import co.elastic.apm.impl.payload.Framework;
import co.elastic.apm.impl.payload.Language;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.RuntimeInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Db;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.context.SpanContext;
import co.elastic.apm.impl.transaction.SpanCount;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.util.HexUtils;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import com.dslplatform.json.BoolConverter;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;
import com.dslplatform.json.StringConverter;
import com.dslplatform.json.UUIDConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.dslplatform.json.JsonWriter.ARRAY_END;
import static com.dslplatform.json.JsonWriter.ARRAY_START;
import static com.dslplatform.json.JsonWriter.COMMA;
import static com.dslplatform.json.JsonWriter.OBJECT_END;
import static com.dslplatform.json.JsonWriter.OBJECT_START;
import static com.dslplatform.json.JsonWriter.QUOTE;

public class DslJsonSerializer implements PayloadSerializer {

    /**
     * Matches default ZLIB buffer size; twice the size of {@link okio.Segment#SIZE}.
     * Lets us assume the ZLIB buffer is always empty,
     * so that {@link #getBufferSize()} is the total amount of buffered bytes.
     */
    public static final int BUFFER_SIZE = 16384;
    private static final byte NEW_LINE = (byte) '\n';
    private final Collection<String> excludedStackFrames = Arrays.asList("java.lang.reflect", "com.sun", "sun.", "jdk.internal.");
    static final int MAX_VALUE_LENGTH = 1024;
    private static final Logger logger = LoggerFactory.getLogger(DslJsonSerializer.class);
    private static final String[] DISALLOWED_IN_TAG_KEY = new String[]{".", "*", "\""};
    // visible for testing
    final JsonWriter jw;
    private final StringBuilder replaceBuilder = new StringBuilder(MAX_VALUE_LENGTH);
    private final DateSerializer dateSerializer;
    private final boolean distributedTracing;
    @Nullable
    private OutputStream os;
    private final StacktraceConfiguration stacktraceConfiguration;

    public DslJsonSerializer(boolean distributedTracingEnabled, StacktraceConfiguration stacktraceConfiguration) {
        this.stacktraceConfiguration = stacktraceConfiguration;
        jw = new DslJson<>().newWriter(BUFFER_SIZE);
        dateSerializer = new DateSerializer();
        distributedTracing = distributedTracingEnabled;
    }

    @Override
    public void setOutputStream(final OutputStream os) {
        if (logger.isTraceEnabled()) {
            this.os = new ByteArrayOutputStream() {
                @Override
                public void flush() throws IOException {
                    os.write(buf);
                    os.flush();
                    logger.trace(new String(buf, Charset.forName("UTF-8")));
                }
            };
        } else {
            this.os = os;
        }
        jw.reset(this.os);
    }

    @Override
    public void flush() throws IOException {
        jw.flush();
        try {
            if (os != null) {
                os.flush();
            }
        } finally {
            jw.reset();
        }
    }

    @Override
    public void serializePayload(final OutputStream os, final Payload payload) {
        if (logger.isTraceEnabled()) {
            logger.trace(toJsonString(payload));
        }
        jw.reset(os);
        if (payload instanceof TransactionPayload) {
            serializeTransactionPayload((TransactionPayload) payload);
        } else if (payload instanceof ErrorPayload) {
            serializeErrorPayload((ErrorPayload) payload);
        }
        jw.flush();
        jw.reset();
    }

    @Override
    public void serializeMetaDataNdJson(MetaData metaData) {
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("metadata");
        jw.writeByte(JsonWriter.OBJECT_START);
        serializeService(metaData.getService());
        jw.writeByte(COMMA);
        serializeProcess(metaData.getProcess());
        jw.writeByte(COMMA);
        serializeSystem(metaData.getSystem());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    @Override
    public void serializeTransactionNdJson(Transaction transaction) {
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("transaction");
        serializeTransaction(transaction);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    @Override
    public void serializeSpanNdJson(Span span) {
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("span");
        serializeSpan(span);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    @Override
    public void serializeErrorNdJson(ErrorCapture error) {
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("error");
        serializeError(error);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    @Override
    public int getBufferSize() {
        return jw.size();
    }

    private void serializeErrorPayload(ErrorPayload payload) {
        jw.writeByte(JsonWriter.OBJECT_START);
        serializeService(payload.getService());
        jw.writeByte(COMMA);
        serializeProcess(payload.getProcess());
        jw.writeByte(COMMA);
        serializeSystem(payload.getSystem());
        jw.writeByte(COMMA);
        serializeErrors(payload.getErrors());
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeErrors(List<ErrorCapture> errors) {
        writeFieldName("errors");
        jw.writeByte(ARRAY_START);
        if (errors.size() > 0) {
            serializeError(errors.get(0));
            for (int i = 1; i < errors.size(); i++) {
                jw.writeByte(COMMA);
                serializeError(errors.get(i));
            }
        }
        jw.writeByte(ARRAY_END);

    }

    private void serializeError(ErrorCapture errorCapture) {
        jw.writeByte(JsonWriter.OBJECT_START);

        writeDateField("timestamp", errorCapture.getTimestamp());

        if (distributedTracing) {
            if (errorCapture.getTraceContext().hasContent()) {
                serializeTraceContext(errorCapture.getTraceContext());
            }
        } else {
            serializeTransactionReference(errorCapture);
        }
        serializeContext(errorCapture.getContext());
        serializeException(errorCapture.getException());

        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeTransactionReference(ErrorCapture errorCapture) {
        if (errorCapture.getTransaction().hasContent()) {
            writeFieldName("transaction");
            jw.writeByte(JsonWriter.OBJECT_START);
            TransactionId transactionId = errorCapture.getTransaction().getTransactionId();
            writeFieldName("id");
            UUIDConverter.serialize(transactionId.getMostSignificantBits(), transactionId.getLeastSignificantBits(), jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeException(@Nullable Throwable exception) {
        writeFieldName("exception");
        jw.writeByte(JsonWriter.OBJECT_START);
        if (exception != null) {
            writeField("message", String.valueOf(exception.getMessage()));
            serializeStacktrace(exception.getStackTrace());
            writeLastField("type", exception.getClass().getName());
        }
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    public String toJsonString(final Payload payload) {
        jw.reset();
        if (payload instanceof TransactionPayload) {
            serializeTransactionPayload((TransactionPayload) payload);
        } else if (payload instanceof ErrorPayload) {
            serializeErrorPayload((ErrorPayload) payload);
        }
        final String s = jw.toString();
        jw.reset();
        return s;
    }

    public String toJsonString(final Transaction transaction) {
        jw.reset();
        serializeTransaction(transaction);
        final String s = jw.toString();
        jw.reset();
        return s;
    }

    public String toJsonString(Span span) {
        jw.reset();
        serializeSpan(span);
        final String s = jw.toString();
        jw.reset();
        return s;
    }

    public String toJsonString(final ErrorCapture error) {
        jw.reset();
        serializeError(error);
        final String s = jw.toString();
        jw.reset();
        return s;
    }

    private void serializeTransactionPayload(final TransactionPayload payload) {
        jw.writeByte(JsonWriter.OBJECT_START);
        serializeService(payload.getService());
        jw.writeByte(COMMA);
        serializeProcess(payload.getProcess());
        jw.writeByte(COMMA);
        serializeSystem(payload.getSystem());
        jw.writeByte(COMMA);
        serializeSpans(payload.getSpans());
        serializeTransactions(payload);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeTransactions(final TransactionPayload payload) {
        writeFieldName("transactions");
        jw.writeByte(ARRAY_START);
        if (payload.getTransactions().size() > 0) {
            serializeTransactions(payload.getTransactions());
        }
        jw.writeByte(ARRAY_END);
    }

    private void serializeService(final Service service) {
        writeFieldName("service");
        jw.writeByte(JsonWriter.OBJECT_START);

        writeField("name", service.getName());
        writeField("environment", service.getEnvironment());

        final Agent agent = service.getAgent();
        if (agent != null) {
            serializeAgent(agent);
        }

        final Framework framework = service.getFramework();
        if (framework != null) {
            serializeFramework(framework);
        }

        final Language language = service.getLanguage();
        if (language != null) {
            serializeLanguage(language);
        }

        final RuntimeInfo runtime = service.getRuntime();
        if (runtime != null) {
            serializeRuntime(runtime);
        }

        writeLastField("version", service.getVersion());
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeAgent(final Agent agent) {
        writeFieldName("agent");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", agent.getName());
        writeLastField("version", agent.getVersion());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeFramework(final Framework framework) {
        writeFieldName("framework");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", framework.getName());
        writeLastField("version", framework.getVersion());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeLanguage(final Language language) {
        writeFieldName("language");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", language.getName());
        writeLastField("version", language.getVersion());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeRuntime(final RuntimeInfo runtime) {
        writeFieldName("runtime");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", runtime.getName());
        writeLastField("version", runtime.getVersion());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeProcess(final ProcessInfo process) {
        writeFieldName("process");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("pid", process.getPid());
        if (process.getPpid() != null) {
            writeField("ppid", process.getPpid());
        }

        List<String> argv = process.getArgv();
        writeField("argv", argv);
        writeLastField("title", process.getTitle());
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeSystem(final SystemInfo system) {
        writeFieldName("system");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("architecture", system.getArchitecture());
        writeField("hostname", system.getHostname());
        writeLastField("platform", system.getPlatform());
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeTransactions(final List<Transaction> transactions) {
        serializeTransaction(transactions.get(0));
        for (int i = 1; i < transactions.size(); i++) {
            jw.writeByte(COMMA);
            serializeTransaction(transactions.get(i));
        }
    }

    private void serializeTransaction(final Transaction transaction) {
        jw.writeByte(OBJECT_START);
        writeDateField("timestamp", transaction.getTimestamp());
        writeField("name", transaction.getName());
        if (distributedTracing) {
            serializeTraceContext(transaction.getTraceContext());
        } else {
            writeField("id", transaction.getId());
        }
        writeField("type", transaction.getType());
        writeField("duration", transaction.getDuration());
        writeField("result", transaction.getResult());
        serializeContext(transaction.getContext());
        if (transaction.getSpanCount().getDropped().getTotal() > 0) {
            serializeSpanCount(transaction.getSpanCount());
        }
        serializeSpans(transaction.getSpans());
        // TODO marks
        writeLastField("sampled", transaction.isSampled());
        jw.writeByte(OBJECT_END);
    }

    private void serializeTraceContext(TraceContext traceContext) {
        writeHexField("trace_id", traceContext.getTraceId().getBytes());
        writeHexField("id", traceContext.getId().getBytes());
        if (traceContext.getParentId().asLong() != 0) {
            writeHexField("parent_id", traceContext.getParentId().getBytes());
        }
    }

    private void serializeSpans(final List<Span> spans) {
        if (spans.size() > 0) {
            writeFieldName("spans");
            jw.writeByte(ARRAY_START);
            serializeSpan(spans.get(0));
            for (int i = 1; i < spans.size(); i++) {
                jw.writeByte(COMMA);
                serializeSpan(spans.get(i));
            }
            jw.writeByte(ARRAY_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeSpan(final Span span) {
        jw.writeByte(OBJECT_START);
        writeField("name", span.getName());
        writeDateField("timestamp", span.getTimestamp());
        if (distributedTracing) {
            serializeTraceContext(span.getTraceContext());
            final Transaction transaction = span.getTransaction();
            if (transaction != null) {
                writeHexField("transaction_id", transaction.getTraceContext().getId().getBytes());
            }
        } else {
            writeField("id", span.getId().asLong());
            final long parent = span.getParent().asLong();
            if (parent != 0) {
                writeField("parent", parent);
            }
        }
        writeField("duration", span.getDuration());
        writeField("start", span.getStart());
        if (span.getStacktrace() != null) {
            serializeStacktrace(span.getStacktrace().getStackTrace());
        }
        if (span.getContext().hasContent()) {
            serializeSpanContext(span.getContext());
        }
        writeLastField("type", span.getType());
        jw.writeByte(OBJECT_END);
    }

    private void serializeStacktrace(StackTraceElement[] stacktrace) {
        if (stacktrace.length > 0) {
            writeFieldName("stacktrace");
            jw.writeByte(ARRAY_START);
            serializeStackTraceArrayElements(stacktrace);
            jw.writeByte(ARRAY_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeStackTraceArrayElements(StackTraceElement[] stacktrace) {

        boolean topMostElasticApmPackagesSkipped = false;
        int collectedStackFrames = 0;
        int stackTraceLimit = stacktraceConfiguration.getStackTraceLimit();
        for (int i = 1; i < stacktrace.length && collectedStackFrames < stackTraceLimit; i++) {
            StackTraceElement stackTraceElement = stacktrace[i];
            // only skip the top most apm stack frames
            if (!topMostElasticApmPackagesSkipped && stackTraceElement.getClassName().startsWith("co.elastic.apm")) {
                continue;
            }
            topMostElasticApmPackagesSkipped = true;

            if (isExcluded(stackTraceElement)) {
                continue;
            }

            if (collectedStackFrames > 0) {
                jw.writeByte(COMMA);
            }
            serializeStackTraceElement(stackTraceElement);
            collectedStackFrames++;
        }
    }

    private boolean isExcluded(StackTraceElement stackTraceElement) {
        // file name is a required field
        if (stackTraceElement.getFileName() == null) {
            return true;
        }
        String className = stackTraceElement.getClassName();
        for (String excludedStackFrame : excludedStackFrames) {
            if (className.startsWith(excludedStackFrame)) {
                return true;
            }
        }
        return false;
    }

    private void serializeStackTraceElement(StackTraceElement stacktrace) {
        jw.writeByte(OBJECT_START);
        writeField("filename", stacktrace.getFileName());
        writeField("function", stacktrace.getMethodName());
        writeField("library_frame", isLibraryFrame(stacktrace.getClassName()));
        writeField("lineno", stacktrace.getLineNumber());
        writeLastField("abs_path", stacktrace.getClassName());
        jw.writeByte(OBJECT_END);
    }

    private boolean isLibraryFrame(String className) {
        for (String applicationPackage : stacktraceConfiguration.getApplicationPackages()) {
            if (className.startsWith(applicationPackage)) {
                return false;
            }
        }
        return true;
    }

    private void serializeSpanContext(SpanContext context) {
        writeFieldName("context");
        jw.writeByte(OBJECT_START);
        writeFieldName("db");
        jw.writeByte(OBJECT_START);
        final Db db = context.getDb();
        writeField("instance", db.getInstance());
        writeField("statement", db.getStatement());
        writeField("type", db.getType());
        writeField("user", db.getUser());
        writeFieldName("tags");
        serializeTags(context.getTags());
        jw.writeByte(OBJECT_END);
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeSpanCount(final SpanCount spanCount) {
        writeFieldName("span_count");
        jw.writeByte(OBJECT_START);
        writeFieldName("dropped");
        jw.writeByte(OBJECT_START);
        writeFieldName("total");
        NumberConverter.serialize(spanCount.getDropped().getTotal(), jw);
        jw.writeByte(OBJECT_END);
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeContext(final TransactionContext context) {
        writeFieldName("context");
        jw.writeByte(OBJECT_START);

        if (context.getUser().hasContent()) {
            serializeUser(context.getUser());
            jw.writeByte(COMMA);
        }
        serializeRequest(context.getRequest());
        serializeResponse(context.getResponse());
        // TODO custom context
        writeFieldName("tags");
        final Map<String, String> value = context.getTags();
        serializeTags(value);
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    // visible for testing
    void serializeTags(Map<String, String> value) {
        jw.writeByte(OBJECT_START);
        final int size = value.size();
        if (size > 0) {
            final Iterator<Map.Entry<String, String>> iterator = value.entrySet().iterator();
            Map.Entry<String, String> kv = iterator.next();
            jw.writeString(sanitizeTagKey(kv.getKey()));
            jw.writeByte(JsonWriter.SEMI);
            StringConverter.serializeNullable(kv.getValue(), jw);
            for (int i = 1; i < size; i++) {
                jw.writeByte(COMMA);
                kv = iterator.next();
                jw.writeString(sanitizeTagKey(kv.getKey()));
                jw.writeByte(JsonWriter.SEMI);
                StringConverter.serializeNullable(kv.getValue(), jw);
            }
        }
        jw.writeByte(OBJECT_END);
    }

    private CharSequence sanitizeTagKey(String key) {
        for (int i = 0; i < DISALLOWED_IN_TAG_KEY.length; i++) {
            if (key.contains(DISALLOWED_IN_TAG_KEY[i])) {
                return replaceAll(key, DISALLOWED_IN_TAG_KEY, "_");
            }
        }
        return key;
    }

    private CharSequence replaceAll(String s, String[] stringsToReplace, String replacement) {
        // uses a instance variable StringBuilder to avoid allocations
        replaceBuilder.setLength(0);
        replaceBuilder.append(s);
        for (String toReplace : stringsToReplace) {
            replace(replaceBuilder, toReplace, replacement);
        }
        return replaceBuilder;
    }

    private void replace(StringBuilder replaceBuilder, String toReplace, String replacement) {
        for (int i = replaceBuilder.indexOf(toReplace); i != -1; i = replaceBuilder.indexOf(toReplace)) {
            replaceBuilder.replace(i, i + replacement.length(), replacement);
        }
    }

    private void serializeResponse(final Response response) {
        if (response.hasContent()) {
            writeFieldName("response");
            jw.writeByte(OBJECT_START);
            writeField("headers", response.getHeaders());
            writeField("finished", response.isFinished());
            writeField("headers_sent", response.isHeadersSent());
            writeFieldName("status_code");
            NumberConverter.serialize(response.getStatusCode(), jw);
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeRequest(final Request request) {
        if (request.hasContent()) {
            writeFieldName("request");
            jw.writeByte(OBJECT_START);
            writeField("method", request.getMethod());
            writeField("headers", request.getHeaders());
            writeField("cookies", request.getCookies());
            // only one of those can be non-empty
            writeField("body", request.getFormUrlEncodedParameters());
            writeField("body", request.getRawBody());
            if (request.getUrl().hasContent()) {
                serializeUrl(request.getUrl());
            }
            if (request.getSocket().hasContent()) {
                serializeSocket(request.getSocket());
            }
            writeLastField("http_version", request.getHttpVersion());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeUrl(final Url url) {
        writeFieldName("url");
        jw.writeByte(OBJECT_START);
        writeField("full", url.getFull());
        writeField("hostname", url.getHostname());
        writeField("port", url.getPort());
        writeField("pathname", url.getPathname());
        writeField("search", url.getSearch());
        writeLastField("protocol", url.getProtocol());
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeSocket(final Socket socket) {
        writeFieldName("socket");
        jw.writeByte(OBJECT_START);
        writeField("encrypted", socket.isEncrypted());
        writeLastField("remote_address", socket.getRemoteAddress());
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void writeField(final String fieldName, final PotentiallyMultiValuedMap map) {
        if (map.size() > 0) {
            writeFieldName(fieldName);
            jw.writeByte(OBJECT_START);
            final int size = map.size();
            if (size > 0) {
                serializePotentiallyMultiValuedEntry(map.getKey(0), map.getValue(0));
                for (int i = 1; i < size; i++) {
                    jw.writeByte(COMMA);
                    serializePotentiallyMultiValuedEntry(map.getKey(i), map.getValue(i));
                }
            }
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializePotentiallyMultiValuedEntry(String key, @Nullable Object value) {
        jw.writeString(key);
        jw.writeByte(JsonWriter.SEMI);
        if (value instanceof String) {
            StringConverter.serialize((String) value, jw);
        } else if (value instanceof List) {
            jw.writeByte(ARRAY_START);
            final List<String> values = (List<String>) value;
            jw.writeString(values.get(0));
            for (int i = 1; i < values.size(); i++) {
                jw.writeByte(COMMA);
                jw.writeString(values.get(i));
            }
            jw.writeByte(ARRAY_END);
        } else if (value == null) {
            jw.writeNull();
        }
    }

    private void serializeUser(final User user) {
        writeFieldName("user");
        jw.writeByte(OBJECT_START);
        writeField("id", user.getId());
        writeField("email", user.getEmail());
        writeLastField("username", user.getUsername());
        jw.writeByte(OBJECT_END);
    }

    void writeField(final String fieldName, final StringBuilder value) {
        if (value.length() > 0) {
            writeFieldName(fieldName);
            writeStringBuilderValue(value);
            jw.writeByte(COMMA);
        }
    }

    void writeField(final String fieldName, @Nullable final String value) {
        if (value != null) {
            writeFieldName(fieldName);
            writeStringValue(value);
            jw.writeByte(COMMA);
        }
    }

    private void writeStringBuilderValue(StringBuilder value) {
        if (value.length() > MAX_VALUE_LENGTH) {
            value.setLength(MAX_VALUE_LENGTH - 1);
            value.append('…');
        }
        jw.writeString(value);
    }

    private void writeStringValue(String value) {
        if (value.length() > MAX_VALUE_LENGTH) {
            replaceBuilder.setLength(0);
            replaceBuilder.append(value, 0, Math.min(value.length(), MAX_VALUE_LENGTH));
            writeStringBuilderValue(replaceBuilder);
        } else {
            jw.writeString(value);
        }
    }

    private void writeField(final String fieldName, final long value) {
        writeFieldName(fieldName);
        NumberConverter.serialize(value, jw);
        jw.writeByte(COMMA);
    }

    private void writeField(final String fieldName, final boolean value) {
        writeFieldName(fieldName);
        BoolConverter.serialize(value, jw);
        jw.writeByte(COMMA);
    }

    private void writeLastField(final String fieldName, final boolean value) {
        writeFieldName(fieldName);
        BoolConverter.serialize(value, jw);
    }

    private void writeField(final String fieldName, final double value) {
        writeFieldName(fieldName);
        NumberConverter.serialize(value, jw);
        jw.writeByte(COMMA);
    }

    void writeLastField(final String fieldName, @Nullable final String value) {
        writeFieldName(fieldName);
        if (value != null) {
            writeStringValue(value);
        } else {
            jw.writeNull();
        }
    }

    private void writeFieldName(final String fieldName) {
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii(fieldName);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
    }

    private void writeField(final String fieldName, final List<String> values) {
        if (values.size() > 0) {
            writeFieldName(fieldName);
            jw.writeByte(ARRAY_START);
            jw.writeString(values.get(0));
            for (int i = 1; i < values.size(); i++) {
                jw.writeByte(COMMA);
                jw.writeString(values.get(i));
            }
            jw.writeByte(ARRAY_END);
            jw.writeByte(COMMA);
        }
    }

    private void writeField(String fieldName, TransactionId id) {
        writeFieldName(fieldName);
        UUIDConverter.serialize(id.getMostSignificantBits(), id.getLeastSignificantBits(), jw);
        jw.writeByte(COMMA);
    }

    private void writeHexField(String fieldName, byte[] value) {
        writeFieldName(fieldName);
        jw.writeByte(JsonWriter.QUOTE);
        HexUtils.writeBytesAsHex(value, jw);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(COMMA);
    }

    private void writeDateField(final String fieldName, final long timestamp) {
        writeFieldName(fieldName);
        jw.writeByte(QUOTE);
        dateSerializer.serializeEpochTimestampAsIsoDateTime(jw, timestamp);
        jw.writeByte(QUOTE);
        jw.writeByte(COMMA);
    }
}
