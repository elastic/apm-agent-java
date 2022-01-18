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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.collections.LongList;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.context.CloudOrigin;
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.Http;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.ServiceOrigin;
import co.elastic.apm.agent.impl.context.Socket;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.User;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.impl.metadata.Agent;
import co.elastic.apm.agent.impl.metadata.CloudProviderInfo;
import co.elastic.apm.agent.impl.metadata.Framework;
import co.elastic.apm.agent.impl.metadata.Language;
import co.elastic.apm.agent.impl.metadata.NameAndIdField;
import co.elastic.apm.agent.impl.metadata.Node;
import co.elastic.apm.agent.impl.metadata.ProcessInfo;
import co.elastic.apm.agent.impl.metadata.RuntimeInfo;
import co.elastic.apm.agent.impl.metadata.Service;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.impl.transaction.FaasTrigger;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.SpanCount;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.util.HexUtils;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import com.dslplatform.json.BoolConverter;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;
import com.dslplatform.json.StringConverter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.elastic.apm.agent.util.ObjectUtils.defaultIfNull;
import static com.dslplatform.json.JsonWriter.ARRAY_END;
import static com.dslplatform.json.JsonWriter.ARRAY_START;
import static com.dslplatform.json.JsonWriter.COMMA;
import static com.dslplatform.json.JsonWriter.OBJECT_END;
import static com.dslplatform.json.JsonWriter.OBJECT_START;
import static com.dslplatform.json.JsonWriter.QUOTE;

public class DslJsonSerializer implements PayloadSerializer {

    /**
     * Matches default ZLIB buffer size.
     * Lets us assume the ZLIB buffer is always empty,
     * so that {@link #getBufferSize()} is the total amount of buffered bytes.
     */
    public static final int BUFFER_SIZE = 16384;
    public static final int MAX_VALUE_LENGTH = 1024;
    public static final int MAX_LONG_STRING_VALUE_LENGTH = 10000;
    private static final byte NEW_LINE = (byte) '\n';
    private static final Logger logger = LoggerFactory.getLogger(DslJsonSerializer.class);
    private static final String[] DISALLOWED_IN_PROPERTY_NAME = new String[]{".", "*", "\""};
    private static final List<String> excludedStackFrames = Arrays.asList("java.lang.reflect", "com.sun", "sun.", "jdk.internal.");
    // visible for testing
    final JsonWriter jw;
    private final StringBuilder replaceBuilder = new StringBuilder(MAX_LONG_STRING_VALUE_LENGTH + 1);
    private final StacktraceConfiguration stacktraceConfiguration;
    private final ApmServerClient apmServerClient;
    @Nullable
    private OutputStream os;

    private final Future<MetaData> metaData;
    @Nullable
    private byte[] serializedMetaData;

    public DslJsonSerializer(StacktraceConfiguration stacktraceConfiguration, ApmServerClient apmServerClient, final Future<MetaData> metaData) {
        this.stacktraceConfiguration = stacktraceConfiguration;
        this.apmServerClient = apmServerClient;
        this.metaData = metaData;
        jw = new DslJson<>(new DslJson.Settings<>()).newWriter(BUFFER_SIZE);
    }

    @Override
    public void setOutputStream(final OutputStream os) {
        if (logger.isTraceEnabled()) {
            this.os = new ByteArrayOutputStream() {
                @Override
                public void flush() throws IOException {
                    os.write(buf, 0, size());
                    os.flush();
                    logger.trace(new String(buf, 0, size(), StandardCharsets.UTF_8));
                }
            };
        } else {
            this.os = os;
        }
        jw.reset(this.os);
    }

    /**
     * Flushes the {@link OutputStream} which has been set via {@link #setOutputStream(OutputStream)}
     * and detaches that {@link OutputStream} from the serializer.
     */
    @Override
    public void fullFlush() throws IOException {
        jw.flush();
        try {
            if (os != null) {
                os.flush();
            }
        } finally {
            jw.reset();
        }
    }

    /**
     * Flushes content that has been written so far to the {@link OutputStream} which has been set
     * via {@link #setOutputStream(OutputStream)}, without flushing the {@link OutputStream} itself.
     * Subsequent serializations will be made to the same {@link OutputStream}.
     */
    @Override
    public void flushToOutputStream() {
        jw.flush();
    }

    /**
     * Appends the serialized metadata to ND-JSON as a {@code metadata} line.
     * <p>
     * NOTE: Must be called after {@link PayloadSerializer#blockUntilReady()} was called and returned, otherwise the
     * cached serialized metadata may not be ready yet.
     * </p>
     *
     * @throws UninitializedException may be thrown if {@link PayloadSerializer#blockUntilReady()} was not invoked
     */
    @Override
    public void appendMetaDataNdJsonToStream() throws UninitializedException {
        assertMetaDataReady();
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("metadata");
        appendMetadataToStream();
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    static void serializeMetadata(MetaData metaData, JsonWriter metadataJW, boolean supportsConfiguredAndDetectedHostname) {
        StringBuilder metadataReplaceBuilder = new StringBuilder();
        metadataJW.writeByte(JsonWriter.OBJECT_START);
        serializeService(metaData.getService(), metadataReplaceBuilder, metadataJW);
        metadataJW.writeByte(COMMA);
        serializeProcess(metaData.getProcess(), metadataReplaceBuilder, metadataJW);
        metadataJW.writeByte(COMMA);
        serializeGlobalLabels(metaData.getGlobalLabelKeys(), metaData.getGlobalLabelValues(), metadataReplaceBuilder, metadataJW);
        serializeSystem(metaData.getSystem(), metadataReplaceBuilder, metadataJW, supportsConfiguredAndDetectedHostname);
        if (metaData.getCloudProviderInfo() != null) {
            metadataJW.writeByte(COMMA);
            serializeCloudProvider(metaData.getCloudProviderInfo(), metadataReplaceBuilder, metadataJW);
        }
        metadataJW.writeByte(JsonWriter.OBJECT_END);
    }

    /**
     * Appends the serialized metadata to the underlying {@link OutputStream}.
     * <p>
     * NOTE: Must be called after {@link PayloadSerializer#blockUntilReady()} was called and returned, otherwise the
     * cached serialized metadata may not be ready yet.
     * </p>
     *
     * @throws UninitializedException may be thrown if {@link PayloadSerializer#blockUntilReady()} was not invoked
     */
    @Override
    public void appendMetadataToStream() throws UninitializedException {
        assertMetaDataReady();
        //noinspection ConstantConditions
        jw.writeAscii(serializedMetaData);
    }

    private void assertMetaDataReady() throws UninitializedException {
        if (serializedMetaData == null) {
            throw new UninitializedException("Cannot serialize metadata as it is not ready yet. Call blockUntilReady()");
        }
    }

    /**
     * Blocking until this {@link PayloadSerializer} is ready for use.
     * Blocking will be timed out with a {@link TimeoutException} if the serializer is not ready within 5 seconds.
     * Since the requirement is to call this method is called on the same thread that calls subsequently calls
     * {@link DslJsonSerializer#appendMetadataToStream()}, there is no risk of visibility issues with regard to
     * {@link DslJsonSerializer#serializedMetaData}.
     *
     * @throws Exception if blocking was interrupted, or timed out or an error occurred in the underlying implementation
     */
    @Override
    public void blockUntilReady() throws Exception {
        if (serializedMetaData == null) {
            JsonWriter metadataJW = new DslJson<>(new DslJson.Settings<>()).newWriter(4096);
            serializeMetadata(metaData.get(5, TimeUnit.SECONDS), metadataJW, apmServerClient.supportsConfiguredAndDetectedHostname());
            serializedMetaData = metadataJW.toByteArray();
        }
    }

    private static void serializeGlobalLabels(ArrayList<String> globalLabelKeys, ArrayList<String> globalLabelValues,
                                              final StringBuilder replaceBuilder, JsonWriter jw) {
        if (!globalLabelKeys.isEmpty()) {
            writeFieldName("labels", jw);
            jw.writeByte(OBJECT_START);
            writeStringValue(sanitizePropertyName(globalLabelKeys.get(0), replaceBuilder), replaceBuilder, jw);
            jw.writeByte(JsonWriter.SEMI);
            writeStringValue(globalLabelValues.get(0), replaceBuilder, jw);
            for (int i = 1; i < globalLabelKeys.size(); i++) {
                jw.writeByte(COMMA);
                writeStringValue(sanitizePropertyName(globalLabelKeys.get(i), replaceBuilder), replaceBuilder, jw);
                jw.writeByte(JsonWriter.SEMI);
                writeStringValue(globalLabelValues.get(i), replaceBuilder, jw);
            }
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
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

    /**
     * Returns the number of bytes already serialized and waiting in the underlying {@link JsonWriter}'s buffer.
     * Note that the resulting JSON can be bigger if a Stream is set to the writer and some data was already flushed
     *
     * @return number of bytes currently waiting in the underlying {@link JsonWriter} to be flushed to the underlying stream
     */
    @Override
    public int getBufferSize() {
        return jw.size();
    }

    @Override
    public void serializeFileMetaData(File file) {
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("metadata");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("log");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeFieldName("file");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("path", file.getAbsolutePath());
        writeLastField("name", file.getName());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(NEW_LINE);
    }

    @Override
    public JsonWriter getJsonWriter() {
        return jw;
    }

    @Override
    public void writeBytes(byte[] bytes, int len) {
        jw.writeAscii(bytes, len);
    }

    private void serializeError(ErrorCapture errorCapture) {
        jw.writeByte(JsonWriter.OBJECT_START);

        writeTimestamp(errorCapture.getTimestamp());
        serializeErrorTransactionInfo(errorCapture.getTransactionInfo());
        if (errorCapture.getTraceContext().hasContent()) {
            serializeTraceContext(errorCapture.getTraceContext(), true);
        }
        serializeContext(null, errorCapture.getContext(), errorCapture.getTraceContext());
        writeField("culprit", errorCapture.getCulprit());
        serializeException(errorCapture.getException());

        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private void serializeErrorTransactionInfo(ErrorCapture.TransactionInfo errorTransactionInfo) {
        writeFieldName("transaction");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", errorTransactionInfo.getName());
        if (errorTransactionInfo.getType() != null) {
            writeField("type", errorTransactionInfo.getType());
        }
        writeLastField("sampled", errorTransactionInfo.isSampled());
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeException(@Nullable Throwable exception) {
        writeFieldName("exception");
        recursiveSerializeException(exception);
    }

    private void recursiveSerializeException(@Nullable Throwable exception) {
        jw.writeByte(JsonWriter.OBJECT_START);
        if (exception != null) {
            writeField("message", String.valueOf(exception.getMessage()));
            serializeStacktrace(exception.getStackTrace());
            writeFieldName("type");
            writeStringValue(exception.getClass().getName());

            Throwable cause = exception.getCause();
            if (cause != null) {
                jw.writeByte(COMMA);
                writeFieldName("cause");
                jw.writeByte(ARRAY_START);
                recursiveSerializeException(cause);
                jw.writeByte(ARRAY_END);
            }
        }
        jw.writeByte(JsonWriter.OBJECT_END);
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

    public String toJsonString(final StackTraceElement stackTraceElement) {
        jw.reset();
        serializeStackTraceElement(stackTraceElement);
        final String s = jw.toString();
        jw.reset();
        return s;
    }

    public String toString() {
        return jw.toString();
    }

    private static void serializeService(final Service service, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("service", jw);
        jw.writeByte(JsonWriter.OBJECT_START);

        writeField("name", service.getName(), replaceBuilder, jw);
        writeField("id", service.getId(), replaceBuilder, jw);
        writeField("environment", service.getEnvironment(), replaceBuilder, jw);

        final Agent agent = service.getAgent();
        if (agent != null) {
            serializeAgent(agent, replaceBuilder, jw);
        }

        final Language language = service.getLanguage();
        if (language != null) {
            serializeLanguage(language, replaceBuilder, jw);
        }

        final Framework framework = service.getFramework();
        if (framework != null) {
            serializeFramework(framework, replaceBuilder, jw);
        }

        final Node node = service.getNode();
        if (node != null && node.hasContents()) {
            serializeNode(node, replaceBuilder, jw);
        }

        final RuntimeInfo runtime = service.getRuntime();
        if (runtime != null) {
            serializeRuntime(runtime, replaceBuilder, jw);
        }

        writeLastField("version", service.getVersion(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeServiceName(final CharSequence serviceName, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (serviceName != null) {
            writeFieldName("service", jw);
            jw.writeByte(OBJECT_START);
            writeLastField("name", serviceName, replaceBuilder, jw);
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private static void serializeAgent(final Agent agent, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("agent", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", agent.getName(), replaceBuilder, jw);
        writeField("ephemeral_id", agent.getEphemeralId(), replaceBuilder, jw);
        writeLastField("version", agent.getVersion(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeFramework(final String frameworkName, final @Nullable String frameworkVersion) {
        writeFieldName("framework");
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("version", frameworkVersion);
        writeLastField("name", frameworkName);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private static void serializeLanguage(final Language language, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("language", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", language.getName(), replaceBuilder, jw);
        writeLastField("version", language.getVersion(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private static void serializeFramework(final Framework framework, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("framework", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", framework.getName(), replaceBuilder, jw);
        writeLastField("version", framework.getVersion(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private static void serializeNode(final Node node, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("node", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        writeLastField("configured_name", node.getName(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private static void serializeRuntime(final RuntimeInfo runtime, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("runtime", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("name", runtime.getName(), replaceBuilder, jw);
        writeLastField("version", runtime.getVersion(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
        jw.writeByte(COMMA);
    }

    private static void serializeProcess(final ProcessInfo process, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("process", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        writeField("pid", process.getPid(), jw);
        if (process.getPpid() != null) {
            writeField("ppid", process.getPpid(), jw);
        }

        List<String> argv = process.getArgv();
        writeField("argv", argv, jw);
        writeLastField("title", process.getTitle(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeSystem(final SystemInfo system, final StringBuilder replaceBuilder, final JsonWriter jw,
                                        boolean supportsConfiguredAndDetectedHostname) {
        writeFieldName("system", jw);
        jw.writeByte(JsonWriter.OBJECT_START);
        serializeContainerInfo(system.getContainerInfo(), replaceBuilder, jw);
        serializeKubernetesInfo(system.getKubernetesInfo(), replaceBuilder, jw);
        writeField("architecture", system.getArchitecture(), replaceBuilder, jw);
        if (supportsConfiguredAndDetectedHostname) {
            String configuredHostname = system.getConfiguredHostname();
            if (configuredHostname != null && !configuredHostname.isEmpty()) {
                writeField("configured_hostname", configuredHostname, replaceBuilder, jw);
            } else {
                String detectedHostname = system.getDetectedHostname();
                if (detectedHostname != null && !detectedHostname.isEmpty()) {
                    writeField("detected_hostname", detectedHostname, replaceBuilder, jw);
                }
            }
        } else {
            writeField("hostname", system.getHostname(), replaceBuilder, jw);
        }
        writeLastField("platform", system.getPlatform(), replaceBuilder, jw);
        jw.writeByte(JsonWriter.OBJECT_END);
    }

    private static void serializeCloudProvider(final CloudProviderInfo cloudProviderInfo, final StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName("cloud", jw);
        jw.writeByte(OBJECT_START);
        serializeNameAndIdField(cloudProviderInfo.getAccount(), "account", replaceBuilder, jw);
        serializeNameAndIdField(cloudProviderInfo.getInstance(), "instance", replaceBuilder, jw);
        serializeNameAndIdField(cloudProviderInfo.getProject(), "project", replaceBuilder, jw);
        if (cloudProviderInfo.getMachine() != null) {
            writeFieldName("machine", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            writeLastField("type", cloudProviderInfo.getMachine().getType(), replaceBuilder, jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
        writeField("availability_zone", cloudProviderInfo.getAvailabilityZone(), replaceBuilder, jw);
        writeField("region", cloudProviderInfo.getRegion(), replaceBuilder, jw);
        if (null != cloudProviderInfo.getService()) {
            writeFieldName("service", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            writeLastField("name", cloudProviderInfo.getService().getName(), replaceBuilder, jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
        writeLastField("provider", cloudProviderInfo.getProvider(), replaceBuilder, jw);
        jw.writeByte(OBJECT_END);
    }

    private static void serializeNameAndIdField(@Nullable NameAndIdField nameAndIdField, String fieldName,
                                                StringBuilder replaceBuilder, JsonWriter jw) {
        if (nameAndIdField != null && !nameAndIdField.isEmpty()) {
            writeFieldName(fieldName, jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            boolean idWritten = false;
            String id = nameAndIdField.getId();
            if (id != null) {
                writeFieldName("id", jw);
                writeStringValue(id, replaceBuilder, jw);
                idWritten = true;
            }
            String name = nameAndIdField.getName();
            if (name != null) {
                if (idWritten) {
                    jw.writeByte(COMMA);
                }
                writeFieldName("name", jw);
                writeStringValue(name, replaceBuilder, jw);
            }
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private static void serializeContainerInfo(@Nullable SystemInfo.Container container, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (container != null) {
            writeFieldName("container", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            writeLastField("id", container.getId(), replaceBuilder, jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private static void serializeKubernetesInfo(@Nullable SystemInfo.Kubernetes kubernetes, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (kubernetes != null && kubernetes.hasContent()) {
            writeFieldName("kubernetes", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            serializeKubeNodeInfo(kubernetes.getNode(), replaceBuilder, jw);
            serializeKubePodInfo(kubernetes.getPod(), replaceBuilder, jw);
            writeLastField("namespace", kubernetes.getNamespace(), replaceBuilder, jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private static void serializeKubePodInfo(@Nullable SystemInfo.Kubernetes.Pod pod, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (pod != null) {
            writeFieldName("pod", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            String podName = pod.getName();
            if (podName != null) {
                writeField("name", podName, replaceBuilder, jw);
            }
            writeLastField("uid", pod.getUid(), replaceBuilder, jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private static void serializeKubeNodeInfo(@Nullable SystemInfo.Kubernetes.Node node, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (node != null) {
            writeFieldName("node", jw);
            jw.writeByte(JsonWriter.OBJECT_START);
            writeLastField("name", node.getName(), replaceBuilder, jw);
            jw.writeByte(JsonWriter.OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeTransaction(final Transaction transaction) {
        TraceContext traceContext = transaction.getTraceContext();

        jw.writeByte(OBJECT_START);
        writeTimestamp(transaction.getTimestamp());
        writeField("name", transaction.getNameForSerialization());
        serializeTraceContext(traceContext, false);
        writeField("type", transaction.getType());
        writeField("duration", transaction.getDurationMs());
        writeField("result", transaction.getResult());
        writeField("outcome", transaction.getOutcome().toString());
        serializeFaas(transaction.getFaas());
        serializeContext(transaction, transaction.getContext(), traceContext);
        serializeSpanCount(transaction.getSpanCount());
        double sampleRate = traceContext.getSampleRate();
        if (!Double.isNaN(sampleRate)) {
            writeField("sample_rate", sampleRate);
        }
        writeLastField("sampled", transaction.isSampled());
        jw.writeByte(OBJECT_END);
    }

    private void serializeTraceContext(TraceContext traceContext, boolean serializeTransactionId) {
        // errors might only have an id
        writeHexField("id", traceContext.getId());
        if (!traceContext.getTraceId().isEmpty()) {
            writeHexField("trace_id", traceContext.getTraceId());
            // transaction_id and parent_id may only be sent alongside a valid trace_id
            if (serializeTransactionId && !traceContext.getTransactionId().isEmpty()) {
                writeHexField("transaction_id", traceContext.getTransactionId());
            }
            if (!traceContext.getParentId().isEmpty()) {
                writeHexField("parent_id", traceContext.getParentId());
            }
        }
    }

    private void serializeSpan(final Span span) {
        TraceContext traceContext = span.getTraceContext();
        jw.writeByte(OBJECT_START);
        writeField("name", span.getNameForSerialization());
        writeTimestamp(span.getTimestamp());

        writeField("outcome", span.getOutcome().toString());
        serializeTraceContext(traceContext, true);
        writeField("duration", span.getDurationMs());
        if (span.getStacktrace() != null) {
            serializeStacktrace(span.getStacktrace().getStackTrace());
        } else if (span.getStackFrames() != null) {
            serializeStackTrace(span.getStackFrames());
        }
        serializeSpanContext(span.getContext(), traceContext);
        writeHexArray("child_ids", span.getChildIds());
        double sampleRate = traceContext.getSampleRate();
        if (!Double.isNaN(sampleRate)) {
            writeField("sample_rate", sampleRate);
        }
        serializeSpanType(span);
        jw.writeByte(OBJECT_END);
    }

    private void serializeServiceNameWithFramework(@Nullable final Transaction transaction, final TraceContext traceContext, final ServiceOrigin serviceOrigin) {
        String serviceName = traceContext.getServiceName();
        boolean isFrameworkNameNotNull = transaction != null && transaction.getFrameworkName() != null;
        if (serviceName != null || isFrameworkNameNotNull || serviceOrigin.hasContent()) {
            writeFieldName("service");
            jw.writeByte(OBJECT_START);
            if (serviceOrigin.hasContent()) {
                serializeServiceOrigin(serviceOrigin);
            }
            if (isFrameworkNameNotNull) {
                serializeFramework(transaction.getFrameworkName(), transaction.getFrameworkVersion());
            }
            writeLastField("name", serviceName);
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeServiceOrigin(final ServiceOrigin serviceOrigin) {
        writeFieldName("origin");
        jw.writeByte(OBJECT_START);
        if (null != serviceOrigin.getId()) {
            writeField("id", serviceOrigin.getId());
        }
        if (null != serviceOrigin.getVersion()) {
            writeField("version", serviceOrigin.getVersion());
        }
        writeLastField("name", serviceOrigin.getName());
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeCloudOrigin(final CloudOrigin cloudOrigin) {
        writeFieldName("cloud");
        jw.writeByte(OBJECT_START);

        writeFieldName("origin");
        jw.writeByte(OBJECT_START);
        if (null != cloudOrigin.getAccountId()) {
            writeFieldName("account");
            jw.writeByte(OBJECT_START);
            writeLastField("id", cloudOrigin.getAccountId());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
        if (null != cloudOrigin.getServiceName()) {
            writeFieldName("service");
            jw.writeByte(OBJECT_START);
            writeLastField("name", cloudOrigin.getServiceName());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
        if (null != cloudOrigin.getRegion()) {
            writeField("region", cloudOrigin.getRegion());
        }
        writeLastField("provider", cloudOrigin.getProvider());
        jw.writeByte(OBJECT_END);

        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    /**
     * TODO: remove in 2.0
     * To be removed for agents working only with APM server 7.0 or higher, where schema contains span.type, span.subtype and span.action
     *
     * @param span serialized span
     */
    private void serializeSpanType(Span span) {
        writeFieldName("type");
        String type = span.getType();
        if (type != null) {
            replaceBuilder.setLength(0);
            replaceBuilder.append(type);
            replace(replaceBuilder, ".", "_", 0);
            String subtype = span.getSubtype();
            String action = span.getAction();
            if (subtype != null || action != null) {
                replaceBuilder.append('.');
                int replaceStartIndex = replaceBuilder.length() + 1;
                if (subtype != null) {
                    replaceBuilder.append(subtype);
                    replace(replaceBuilder, ".", "_", replaceStartIndex);
                }
                if (action != null) {
                    replaceBuilder.append('.');
                    replaceStartIndex = replaceBuilder.length() + 1;
                    replaceBuilder.append(action);
                    replace(replaceBuilder, ".", "_", replaceStartIndex);
                }
            }
            writeStringValue(replaceBuilder);
        } else {
            jw.writeNull();
        }
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
        if (stackTraceLimit < 0) {
            stackTraceLimit = stacktrace.length;
        }
        for (int i = 0; i < stacktrace.length && collectedStackFrames < stackTraceLimit; i++) {
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

    private static boolean isExcluded(StackTraceElement stackTraceElement) {
        // file name is a required field
        if (stackTraceElement.getFileName() == null) {
            return true;
        }
        String className = stackTraceElement.getClassName();
        for (int i = 0, size = excludedStackFrames.size(); i < size; i++) {
            if (className.startsWith(excludedStackFrames.get(i))) {
                return true;
            }
        }
        return false;
    }

    private void serializeStackTraceElement(StackTraceElement stacktrace) {
        jw.writeByte(OBJECT_START);
        writeField("filename", stacktrace.getFileName());
        writeField("classname", stacktrace.getClassName());
        writeField("function", stacktrace.getMethodName());
        writeField("library_frame", isLibraryFrame(stacktrace.getClassName()));
        writeField("lineno", stacktrace.getLineNumber());
        serializeStackFrameModule(stacktrace.getClassName());
        jw.writeByte(OBJECT_END);
    }

    private void serializeStackFrameModule(final String fullyQualifiedClassName) {
        writeFieldName("module");
        replaceBuilder.setLength(0);
        final int lastDotIndex = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            replaceBuilder.append(fullyQualifiedClassName, 0, lastDotIndex);
        }
        writeStringBuilderValue(replaceBuilder, jw);
    }

    private boolean isLibraryFrame(String className) {
        for (String applicationPackage : stacktraceConfiguration.getApplicationPackages()) {
            if (className.startsWith(applicationPackage)) {
                return false;
            }
        }
        return true;
    }

    private void serializeStackTrace(List<StackFrame> stackTrace) {
        if (stackTrace.isEmpty()) {
            return;
        }
        writeFieldName("stacktrace");
        jw.writeByte(ARRAY_START);
        StringBuilder replaceBuilder = this.replaceBuilder;
        for (int i = 0, size = stackTrace.size(); i < size; i++) {
            if (i != 0) {
                jw.writeByte(COMMA);
            }
            serializeStackTraceElement(stackTrace.get(i), replaceBuilder);
        }
        jw.writeByte(ARRAY_END);
        jw.writeByte(COMMA);
    }

    private void serializeStackTraceElement(StackFrame frame, StringBuilder replaceBuilder) {
        jw.writeByte(OBJECT_START);

        replaceBuilder.setLength(0);
        frame.appendFileName(replaceBuilder);
        writeField("filename", replaceBuilder);
        writeField("function", frame.getMethodName());
        writeField("library_frame", isLibraryFrame(frame.getClassName()));
        writeLastField("lineno", -1);
        jw.writeByte(OBJECT_END);
    }

    private void serializeSpanContext(SpanContext context, TraceContext traceContext) {
        writeFieldName("context");
        jw.writeByte(OBJECT_START);

        serializeServiceName(traceContext.getServiceName(), replaceBuilder, jw);
        serializeMessageContext(context.getMessage());
        serializeDbContext(context.getDb());
        serializeHttpContext(context.getHttp());
        serializeDestination(context.getDestination());

        writeFieldName("tags");
        serializeLabels(context);

        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeDestination(Destination destination) {
        if (destination.hasContent()) {
            writeFieldName("destination");
            jw.writeByte(OBJECT_START);
            boolean hasAddress = destination.getAddress().length() > 0;
            boolean hasPort = destination.getPort() > 0;
            boolean hasServiceContent = destination.getService().hasContent();
            if (hasAddress) {
                if (hasPort || hasServiceContent) {
                    writeField("address", destination.getAddress());
                } else {
                    writeLastField("address", destination.getAddress());
                }
            }
            if (hasPort) {
                if (hasServiceContent) {
                    writeField("port", destination.getPort());
                } else {
                    writeLastField("port", destination.getPort());
                }
            }
            serializeService(hasServiceContent, destination.getService());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeService(boolean isServiceHasContent, Destination.Service service) {
        if (isServiceHasContent) {
            writeFieldName("service");
            jw.writeByte(OBJECT_START);
            writeEmptyField("name");
            writeEmptyField("type");
            writeLastField("resource", service.getResource());
            jw.writeByte(OBJECT_END);
        }
    }

    private void serializeMessageContext(final Message message) {
        if (message.hasContent()) {
            writeFieldName("message");
            jw.writeByte(OBJECT_START);
            StringBuilder body = message.getBodyForRead();
            if (body != null && body.length() > 0) {
                writeLongStringField("body", message.getBodyForWrite());
            }
            serializeMessageHeaders(message.getHeaders());
            int messageAge = (int) message.getAge();
            if (messageAge >= 0) {
                writeFieldName("age");
                jw.writeByte(OBJECT_START);
                writeLastField("ms", messageAge);
                jw.writeByte(OBJECT_END);
                jw.writeByte(COMMA);
            }
            if (message.getRoutingKey() != null && !message.getRoutingKey().isEmpty()) {
                writeField("routing_key", message.getRoutingKey());
            }
            writeFieldName("queue");
            jw.writeByte(OBJECT_START);
            writeLastField("name", message.getQueueName());
            jw.writeByte(OBJECT_END);

            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeMessageHeaders(Headers headers) {
        if (!headers.isEmpty()) {
            writeFieldName("headers");
            jw.writeByte(OBJECT_START);
            Iterator<Headers.Header> iterator = headers.iterator();
            while (iterator.hasNext()) {
                Headers.Header header = iterator.next();
                if (iterator.hasNext()) {
                    writeField(header.getKey(), header.getValue(), replaceBuilder, jw, true);
                } else {
                    writeLastField(header.getKey(), header.getValue(), replaceBuilder, jw);
                }
            }
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeFaas(final Faas faas) {
        if (faas.hasContent()) {
            writeFieldName("faas");
            jw.writeByte(OBJECT_START);
            writeField("execution", faas.getExecution());
            serializeFaasTrigger(faas.getTrigger());
            writeLastField("coldstart", faas.isColdStart());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeFaasTrigger(final FaasTrigger trigger) {
        if (trigger.hasContent()) {
            writeFieldName("trigger");
            jw.writeByte(OBJECT_START);
            writeField("request_id", trigger.getRequestId());
            writeLastField("type", trigger.getType());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeDbContext(final Db db) {
        if (db.hasContent()) {
            writeFieldName("db");
            jw.writeByte(OBJECT_START);
            writeField("instance", db.getInstance());
            String statement = db.getStatement();
            if (statement != null) {
                writeLongStringField("statement", statement);
            } else {
                final CharBuffer statementBuffer = db.getStatementBuffer();
                if (statementBuffer != null && statementBuffer.length() > 0) {
                    writeFieldName("statement");
                    jw.writeString(statementBuffer);
                    jw.writeByte(COMMA);
                }
            }
            long affectedRows = db.getAffectedRowsCount();
            if (affectedRows >= 0) {
                // a negative value generally indicates that feature is not supported by db/driver
                // thus we just do not report them
                writeField("rows_affected", affectedRows);
            }
            writeField("type", db.getType());
            writeField("link", db.getDbLink());
            writeLastField("user", db.getUser());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeHttpContext(final Http http) {
        if (http.hasContent()) {
            writeFieldName("http");
            jw.writeByte(OBJECT_START);
            writeField("method", http.getMethod());
            int statusCode = http.getStatusCode();
            if (statusCode > 0) {
                writeField("status_code", http.getStatusCode());
            }
            writeLastField("url", http.getUrl());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private void serializeSpanCount(final SpanCount spanCount) {
        writeFieldName("span_count");
        jw.writeByte(OBJECT_START);
        writeField("dropped", spanCount.getDropped().get());
        writeLastField("started", spanCount.getReported().get());
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void serializeContext(@Nullable final Transaction transaction, final TransactionContext context, TraceContext traceContext) {
        writeFieldName("context");
        jw.writeByte(OBJECT_START);
        serializeServiceNameWithFramework(transaction, traceContext, context.getServiceOrigin());

        if (context.getUser().hasContent()) {
            serializeUser(context.getUser());
            jw.writeByte(COMMA);
        }
        serializeRequest(context.getRequest());
        serializeResponse(context.getResponse());
        serializeMessageContext(context.getMessage());
        if (context.hasCustom()) {
            writeFieldName("custom");
            serializeStringKeyScalarValueMap(context.getCustomIterator(), replaceBuilder, jw, true, true);
            jw.writeByte(COMMA);
        }
        if (context.getCloudOrigin().hasContent()) {
            serializeCloudOrigin(context.getCloudOrigin());
        }
        writeFieldName("tags");
        serializeLabels(context);
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    // visible for testing
    void serializeLabels(AbstractContext context) {
        if (context.hasLabels()) {
            serializeStringKeyScalarValueMap(context.getLabelIterator(), replaceBuilder, jw, false, apmServerClient.supportsNonStringLabels());
        } else {
            jw.writeByte(OBJECT_START);
            jw.writeByte(OBJECT_END);
        }
    }

    private static void serializeStringKeyScalarValueMap(Iterator<? extends Map.Entry<String, ? /* String|Number|Boolean */>> it,
                                                         final StringBuilder replaceBuilder, final JsonWriter jw, boolean extendedStringLimit,
                                                         boolean supportsNonStringValues) {
        jw.writeByte(OBJECT_START);
        if (it.hasNext()) {
            Map.Entry<String, ?> kv = it.next();
            writeStringValue(sanitizePropertyName(kv.getKey(), replaceBuilder), replaceBuilder, jw);
            jw.writeByte(JsonWriter.SEMI);
            serializeScalarValue(replaceBuilder, jw, kv.getValue(), extendedStringLimit, supportsNonStringValues);
            while (it.hasNext()) {
                jw.writeByte(COMMA);
                kv = it.next();
                writeStringValue(sanitizePropertyName(kv.getKey(), replaceBuilder), replaceBuilder, jw);
                jw.writeByte(JsonWriter.SEMI);
                serializeScalarValue(replaceBuilder, jw, kv.getValue(), extendedStringLimit, supportsNonStringValues);
            }
        }
        jw.writeByte(OBJECT_END);
    }

    static void serializeLabels(Labels labels, final String serviceName, final StringBuilder replaceBuilder, final JsonWriter jw) {
        serializeServiceName(defaultIfNull(labels.getServiceName(), serviceName), replaceBuilder, jw);
        if (!labels.isEmpty()) {
            if (labels.getTransactionName() != null || labels.getTransactionType() != null) {
                writeFieldName("transaction", jw);
                jw.writeByte(OBJECT_START);
                writeField("name", labels.getTransactionName(), replaceBuilder, jw);
                writeLastField("type", labels.getTransactionType(), replaceBuilder, jw);
                jw.writeByte(OBJECT_END);
                jw.writeByte(COMMA);
            }

            if (labels.getSpanType() != null || labels.getSpanSubType() != null) {
                writeFieldName("span", jw);
                jw.writeByte(OBJECT_START);
                writeField("type", labels.getSpanType(), replaceBuilder, jw);
                writeLastField("subtype", labels.getSpanSubType(), replaceBuilder, jw);
                jw.writeByte(OBJECT_END);
                jw.writeByte(COMMA);
            }

            writeFieldName("tags", jw);
            jw.writeByte(OBJECT_START);
            serialize(labels, replaceBuilder, jw);
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    private static void serialize(Labels labels, final StringBuilder replaceBuilder, final JsonWriter jw) {
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                jw.writeByte(COMMA);
            }
            writeStringValue(sanitizePropertyName(labels.getKey(i), replaceBuilder), replaceBuilder, jw);
            jw.writeByte(JsonWriter.SEMI);
            serializeScalarValue(replaceBuilder, jw, labels.getValue(i), false, false);
        }
    }

    private static void serializeScalarValue(final StringBuilder replaceBuilder, final JsonWriter jw, Object value, boolean extendedStringLimit, boolean supportsNonStringValues) {
        if (value instanceof String) {
            if (extendedStringLimit) {
                writeLongStringValue((String) value, replaceBuilder, jw);
            } else {
                writeStringValue((String) value, replaceBuilder, jw);
            }
        } else if (value instanceof Number) {
            if (supportsNonStringValues) {
                NumberConverter.serialize(((Number) value).doubleValue(), jw);
            } else {
                jw.writeNull();
            }
        } else if (value instanceof Boolean) {
            if (supportsNonStringValues) {
                BoolConverter.serialize((Boolean) value, jw);
            } else {
                jw.writeNull();
            }
        } else {
            // can't happen, as AbstractContext enforces the values to be either String, Number or boolean
            jw.writeString("invalid value");
        }
    }

    public static CharSequence sanitizePropertyName(String key, StringBuilder replaceBuilder) {
        for (int i = 0; i < DISALLOWED_IN_PROPERTY_NAME.length; i++) {
            if (key.contains(DISALLOWED_IN_PROPERTY_NAME[i])) {
                return replaceAll(key, DISALLOWED_IN_PROPERTY_NAME, "_", replaceBuilder);
            }
        }
        return key;
    }

    private static CharSequence replaceAll(String s, String[] stringsToReplace, String replacement, StringBuilder replaceBuilder) {
        // uses a instance variable StringBuilder to avoid allocations
        replaceBuilder.setLength(0);
        replaceBuilder.append(s);
        for (String toReplace : stringsToReplace) {
            replace(replaceBuilder, toReplace, replacement, 0);
        }
        return replaceBuilder;
    }

    static void replace(StringBuilder replaceBuilder, String toReplace, String replacement, int fromIndex) {
        for (int i = replaceBuilder.indexOf(toReplace, fromIndex); i != -1; i = replaceBuilder.indexOf(toReplace, fromIndex)) {
            replaceBuilder.replace(i, i + toReplace.length(), replacement);
            fromIndex = i;
        }
    }

    private void serializeResponse(final Response response) {
        if (response.hasContent()) {
            writeFieldName("response");
            jw.writeByte(OBJECT_START);
            writeField("headers", response.getHeaders(), apmServerClient.supportsMultipleHeaderValues());
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
            writeField("headers", request.getHeaders(), apmServerClient.supportsMultipleHeaderValues());
            writeField("cookies", request.getCookies(), apmServerClient.supportsMultipleHeaderValues());
            // only one of those can be non-empty
            if (!request.getFormUrlEncodedParameters().isEmpty()) {
                writeField("body", request.getFormUrlEncodedParameters(), true);
            } else if (request.getRawBody() != null) {
                writeField("body", request.getRawBody());
            } else {
                final CharSequence bodyBuffer = request.getBodyBufferForSerialization();
                if (bodyBuffer != null && bodyBuffer.length() > 0) {
                    writeFieldName("body");
                    jw.writeString(bodyBuffer);
                    jw.writeByte(COMMA);
                }
            }
            if (request.getUrl().hasContent()) {
                writeFieldName("url");
                serializeUrl(request.getUrl());
                jw.writeByte(COMMA);
            }
            if (request.getSocket().hasContent()) {
                serializeSocket(request.getSocket());
            }
            writeLastField("http_version", request.getHttpVersion());
            jw.writeByte(OBJECT_END);
            jw.writeByte(COMMA);
        }
    }

    // visible for testing
    void serializeUrl(final Url url) {
        jw.writeByte(OBJECT_START);
        writeField("full", url.getFull());
        writeField("hostname", url.getHostname());
        int port = url.getPort();
        if (apmServerClient.supportsNumericUrlPort()) {
            writeField("port", port);
        } else {
            // serialize as a string for compatibility
            // doing it in low-level to avoid allocation
            writeFieldName("port", jw);
            jw.writeByte(QUOTE);
            NumberConverter.serialize(port, jw);
            jw.writeByte(QUOTE);
            jw.writeByte(COMMA);
        }
        writeField("pathname", url.getPathname());
        writeField("search", url.getSearch());
        writeLastField("protocol", url.getProtocol());
        jw.writeByte(OBJECT_END);
    }

    private void serializeSocket(final Socket socket) {
        writeFieldName("socket");
        jw.writeByte(OBJECT_START);
        writeLastField("remote_address", socket.getRemoteAddress());
        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
    }

    private void writeField(final String fieldName, final PotentiallyMultiValuedMap map, boolean supportsMultipleValues) {
        if (map.isEmpty()) {
            return;
        }

        writeFieldName(fieldName);
        jw.writeByte(OBJECT_START);
        int size = map.size();
        if(supportsMultipleValues){
            serializePotentiallyMultiValuedEntry(map.getKey(0), map.getValue(0));
            for (int i = 1; i < size; i++) {
                jw.writeByte(COMMA);
                serializePotentiallyMultiValuedEntry(map.getKey(i), map.getValue(i));
            }
        } else {
            int last = size - 1;
            for (int i = 0; i <= last; i++) {
                String key = map.getKey(i);
                String value = map.getFirst(key);
                if (i == last) {
                    writeLastField(key, value);
                } else {
                    writeField(key, value);
                }
            }
        }

        jw.writeByte(OBJECT_END);
        jw.writeByte(COMMA);
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
        writeField("domain", user.getDomain());
        writeField("id", user.getId());
        writeField("email", user.getEmail());
        writeLastField("username", user.getUsername());
        jw.writeByte(OBJECT_END);
    }

    void writeEmptyField(final String fieldName) {
        writeFieldName(fieldName);
        writeStringValue("");
        jw.writeByte(COMMA);
    }

    void writeField(final String fieldName, final StringBuilder value) {
        if (value.length() > 0) {
            writeFieldName(fieldName);
            writeStringBuilderValue(value);
            jw.writeByte(COMMA);
        }
    }


    void writeLongStringField(final String fieldName, @Nullable final CharSequence value) {
        if (value != null) {
            writeFieldName(fieldName);
            writeLongStringValue(value);
            jw.writeByte(COMMA);
        }
    }

    void writeField(final String fieldName, @Nullable final CharSequence value) {
        writeField(fieldName, value, replaceBuilder, jw);
    }

    static void writeField(final String fieldName,
                           @Nullable final CharSequence value,
                           final StringBuilder replaceBuilder,
                           final JsonWriter jw) {

        writeField(fieldName, value, replaceBuilder, jw, false);
    }

    static void writeField(final String fieldName,
                           @Nullable final CharSequence value,
                           final StringBuilder replaceBuilder,
                           final JsonWriter jw,
                           boolean writeNull) {

        if (value == null) {
            if (writeNull) {
                writeFieldName(fieldName, jw);
                jw.writeNull();
                jw.writeByte(COMMA);
            }
        } else {
            writeFieldName(fieldName, jw);
            writeStringValue(value, replaceBuilder, jw);
            jw.writeByte(COMMA);
        }
    }

    private void writeStringBuilderValue(StringBuilder value) {
        writeStringBuilderValue(value, jw);
    }

    private static void writeStringBuilderValue(StringBuilder value, JsonWriter jw) {
        if (value.length() > MAX_VALUE_LENGTH) {
            value.setLength(MAX_VALUE_LENGTH - 1);
            value.append('…');
        }
        jw.writeString(value);
    }

    private void writeStringValue(CharSequence value) {
        writeStringValue(value, replaceBuilder, jw);
    }

    public static void writeStringValue(CharSequence value, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (value.length() > MAX_VALUE_LENGTH) {
            replaceBuilder.setLength(0);
            replaceBuilder.append(value, 0, Math.min(value.length(), MAX_VALUE_LENGTH + 1));
            writeStringBuilderValue(replaceBuilder, jw);
        } else {
            jw.writeString(value);
        }
    }

    private static void writeLongStringBuilderValue(StringBuilder value, JsonWriter jw) {
        if (value.length() > MAX_LONG_STRING_VALUE_LENGTH) {
            value.setLength(MAX_LONG_STRING_VALUE_LENGTH - 1);
            value.append('…');
        }
        jw.writeString(value);
    }

    private void writeLongStringValue(CharSequence value) {
        writeLongStringValue(value, replaceBuilder, jw);
    }

    private static void writeLongStringValue(CharSequence value, final StringBuilder replaceBuilder, final JsonWriter jw) {
        if (value.length() > MAX_LONG_STRING_VALUE_LENGTH) {
            replaceBuilder.setLength(0);
            replaceBuilder.append(value, 0, Math.min(value.length(), MAX_LONG_STRING_VALUE_LENGTH + 1));
            writeLongStringBuilderValue(replaceBuilder, jw);
        } else {
            jw.writeString(value);
        }
    }

    static void writeField(final String fieldName, final long value, final JsonWriter jw) {
        writeFieldName(fieldName, jw);
        NumberConverter.serialize(value, jw);
        jw.writeByte(COMMA);
    }

    private void writeField(final String fieldName, final long value) {
        writeField(fieldName, value, jw);
    }

    private void writeField(final String fieldName, final int value) {
        writeFieldName(fieldName);
        NumberConverter.serialize(value, jw);
        jw.writeByte(COMMA);
    }

    private void writeLastField(final String fieldName, final int value) {
        writeFieldName(fieldName);
        NumberConverter.serialize(value, jw);
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

    void writeLastField(final String fieldName, @Nullable final CharSequence value) {
        writeLastField(fieldName, value, replaceBuilder, jw);
    }

    public static void writeLastField(final String fieldName, @Nullable final CharSequence value, StringBuilder replaceBuilder, final JsonWriter jw) {
        writeFieldName(fieldName, jw);
        if (value != null && value.length() > 0) {
            writeStringValue(value, replaceBuilder, jw);
        } else {
            jw.writeNull();
        }
    }

    public static void writeFieldName(final String fieldName, final JsonWriter jw) {
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeAscii(fieldName);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(JsonWriter.SEMI);
    }

    private void writeFieldName(final String fieldName) {
        writeFieldName(fieldName, jw);
    }

    static void writeField(final String fieldName, final List<String> values, final JsonWriter jw) {
        if (values.size() > 0) {
            writeFieldName(fieldName, jw);
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

    private void writeHexField(String fieldName, Id traceId) {
        writeFieldName(fieldName);
        jw.writeByte(JsonWriter.QUOTE);
        traceId.writeAsHex(jw);
        jw.writeByte(JsonWriter.QUOTE);
        jw.writeByte(COMMA);
    }

    private void writeTimestamp(final long epochMicros) {
        writeFieldName("timestamp");
        NumberConverter.serialize(epochMicros, jw);
        jw.writeByte(COMMA);
    }

    private void writeHexArray(String fieldName, @Nullable LongList longList) {
        if (longList != null && longList.getSize() > 0) {
            writeFieldName(fieldName);
            jw.writeByte(ARRAY_START);
            for (int i = 0, size = longList.getSize(); i < size; i++) {
                if (i > 0) {
                    jw.writeByte(COMMA);
                }
                jw.writeByte(QUOTE);
                HexUtils.writeAsHex(longList.get(i), jw);
                jw.writeByte(QUOTE);
            }
            jw.writeByte(ARRAY_END);
            jw.writeByte(COMMA);
        }
    }
}
