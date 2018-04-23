/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.context.Socket;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.impl.context.User;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.error.ErrorPayload;
import co.elastic.apm.impl.error.ExceptionInfo;
import co.elastic.apm.impl.payload.Agent;
import co.elastic.apm.impl.payload.Framework;
import co.elastic.apm.impl.payload.Language;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.RuntimeInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.impl.transaction.Db;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.SpanContext;
import co.elastic.apm.impl.transaction.SpanCount;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import com.dslplatform.json.BoolConverter;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.MapConverter;
import com.dslplatform.json.NumberConverter;
import com.dslplatform.json.StringConverter;
import com.dslplatform.json.UUIDConverter;
import okio.BufferedSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.dslplatform.json.JsonWriter.ARRAY_END;
import static com.dslplatform.json.JsonWriter.ARRAY_START;
import static com.dslplatform.json.JsonWriter.COMMA;
import static com.dslplatform.json.JsonWriter.OBJECT_END;
import static com.dslplatform.json.JsonWriter.OBJECT_START;

public class DslJsonSerializer implements PayloadSerializer {

    private static final Logger logger = LoggerFactory.getLogger(DslJsonSerializer.class);

    private final JsonWriter jw;
    private final DateFormat dateFormat;

    public DslJsonSerializer() {
        jw = new DslJson<>().newWriter();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void serializePayload(final BufferedSink sink, final Payload payload) {
        if (logger.isTraceEnabled()) {
            logger.trace(toJsonString(payload));
        }
        jw.reset(sink.outputStream());
        if (payload instanceof TransactionPayload) {
            serializeTransactionPayload((TransactionPayload) payload);
        } else if (payload instanceof ErrorPayload) {
            serializeErrorPayload((ErrorPayload) payload);
        }
        jw.flush();
        jw.reset();
    }

    private void serializeErrorPayload(ErrorPayload payload) {
        jw.writeByte(JsonWriter.OBJECT_START);
        serializeService(payload.getService());
        serializeProcess(payload.getProcess());
        serializeSystem(payload.getSystem());
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

        final TransactionId id = errorCapture.getId();
        final String fieldName = "id";
        writeField(fieldName, id);

        serializeTransactionReference(errorCapture);
        serializeContext(errorCapture.getContext());
        serializeException(errorCapture.getException());

        // TODO date formatting allocates objects
        // writeLastField("timestamp", errorCapture.getTimestamp().getTime());
        writeLastField("timestamp", dateFormat.format(errorCapture.getTimestamp()));
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeTransactionReference(ErrorCapture errorCapture) {
        final TransactionId transactionId = errorCapture.getTransaction().getId();
        if (!transactionId.isEmpty()) {
            writeFieldName("transaction");
            jw.writeByte(JsonWriter.OBJECT_START);
            writeFieldName("id");
            UUIDConverter.serialize(transactionId.getMostSignificantBits(), transactionId.getLeastSignificantBits(), jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeException(ExceptionInfo exception) {
        writeFieldName("exception");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("code", exception.getCode());
        writeField("message", exception.getMessage());
        serializeStacktrace(exception.getStacktrace());
        writeLastField("type", exception.getType());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
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
        serializeProcess(payload.getProcess());
        serializeSystem(payload.getSystem());
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
        jw.writeByte(COMMA);
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
        jw.writeByte(COMMA);
    }

    private void serializeSystem(final SystemInfo system) {
        writeFieldName("system");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("architecture", system.getArchitecture());
        writeField("hostname", system.getHostname());
        writeLastField("platform", system.getPlatform());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
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
        // TODO date formatting allocates objects
        // writeField("timestamp", transaction.getTimestamp().getTime());
        writeField("timestamp", dateFormat.format(transaction.getTimestamp()));
        writeField("name", transaction.getName());
        writeField("id", transaction.getId());
        writeField("type", transaction.getType());
        writeField("duration", transaction.getDuration());
        writeField("result", transaction.getResult());
        serializeContext(transaction.getContext());
        if (transaction.getSpanCount().getDropped().getTotal() > 0) {
            serializeSpanCount(transaction.getSpanCount());
        }
        if (transaction.getSpans().size() > 0) {
            serializeSpans(transaction.getSpans());
        }
        // TODO marks
        writeLastField("sampled", transaction.isSampled());
        jw.writeByte(OBJECT_END);
    }

    private void serializeSpans(final List<Span> spans) {
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

    private void serializeSpan(final Span span) {
        jw.writeByte(OBJECT_START);
        writeField("name", span.getName());
        writeField("id", span.getId().asLong());
        final long parent = span.getParent().asLong();
        if (parent != 0) {
            writeField("parent", parent);
        }
        writeField("duration", span.getDuration());
        writeField("start", span.getStart());
        writeField("type", span.getType());
        if (span.getStacktrace().size() > 0) {
            serializeStacktrace(span.getStacktrace());
        }
        if (span.getContext().hasContent()) {
            serializeSpanContext(span.getContext());
        }
        writeLastField("sampled", span.isSampled());
        jw.writeByte(OBJECT_END);
    }

    private void serializeStacktrace(List<Stacktrace> stacktrace) {
        if (stacktrace.size() > 0) {
            writeFieldName("stacktrace");
            jw.writeByte(ARRAY_START);
            serializeStackTraceElement(stacktrace.get(0));
            for (int i = 1; i < stacktrace.size(); i++) {
                jw.writeByte(COMMA);
                serializeStackTraceElement(stacktrace.get(i));
            }
            jw.writeByte(ARRAY_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeStackTraceElement(Stacktrace stacktrace) {
        jw.writeByte(OBJECT_START);
        writeField("filename", stacktrace.getFilename());
        writeField("function", stacktrace.getFunction());
        writeField("library_frame", stacktrace.isLibraryFrame());
        writeField("lineno", stacktrace.getLineno());
        writeField("module", stacktrace.getModule());
        writeLastField("abs_path", stacktrace.getAbsPath());
        jw.writeByte(OBJECT_END);
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
        writeLastField("user", db.getUser());
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

    private void serializeContext(final Context context) {
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
        MapConverter.serialize(context.getTags(), jw);
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
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
        writeField("protocol", url.getProtocol());
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

    private void writeField(final String fieldName, final PotentiallyMultiValuedMap<String, String> map) {
        if (map.size() > 0) {
            writeFieldName(fieldName);
            jw.writeByte(OBJECT_START);
            final int size = map.size();
            if (size > 0) {
                final Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
                Map.Entry<String, Object> kv = iterator.next();
                serializePotentiallyMultiValuedEntry(kv);
                for (int i = 1; i < size; i++) {
                    jw.writeByte(COMMA);
                    kv = iterator.next();
                    serializePotentiallyMultiValuedEntry(kv);
                }
            }
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializePotentiallyMultiValuedEntry(final Map.Entry<String, Object> entry) {
        jw.writeString(entry.getKey());
        jw.writeByte(JsonWriter.SEMI);
        final Object value = entry.getValue();
        if (value instanceof String) {
            StringConverter.serializeNullable((String) value, jw);
        } else if (value instanceof List) {
            jw.writeByte(ARRAY_START);
            final List<String> values = (List<String>) value;
            jw.writeString(values.get(0));
            for (int i = 1; i < values.size(); i++) {
                jw.writeByte(COMMA);
                jw.writeString(values.get(i));
            }
            jw.writeByte(ARRAY_END);
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

    private void writeField(final String fieldName, final StringBuilder value) {
        // TODO limit size of value to 1024
        if (value.length() > 0) {
            writeFieldName(fieldName);
            jw.writeString(value);
            jw.writeByte(COMMA);
        }
    }

    private void writeField(final String fieldName, @Nullable final String value) {
        // TODO limit size of value to 1024
        if (value != null) {
            writeFieldName(fieldName);
            jw.writeString(value);
            jw.writeByte(COMMA);
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

    private void writeLastField(final String fieldName, @Nullable final String value) {
        writeFieldName(fieldName);
        if (value != null) {
            jw.writeString(value);
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
}
