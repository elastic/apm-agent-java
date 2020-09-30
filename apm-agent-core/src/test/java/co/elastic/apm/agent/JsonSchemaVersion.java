/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent;

import co.elastic.apm.agent.util.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

enum JsonSchemaVersion {

    V6_5(Version.of("6.5.0"), "apm-server-schema/v6.5.0",
        "transactions/v2_transaction.json",
        "spans/v2_span.json",
        "errors/v2_error.json"
    ),

    CURRENT(null, "apm-server-schema/current",
        "transactions/transaction.json",
        "spans/span.json",
        "errors/error.json");

    public static final String SERVER_SPEC_PATH_PREFIX = "docs/spec/";
    public static final String REF = "$ref";
    public static final String ID = "$id";

    public static JsonSchemaVersion current() {
        return CURRENT;
    }

    private final JsonSchema transactionSchema;
    private final JsonSchema spanSchema;
    private final JsonSchema errorSchema;

    private final String basePath;
    @Nullable
    private final Version version;

    JsonSchemaVersion(@Nullable Version version, String basePath, String transactionFile, String spanFile, String errorFile) {
        this.basePath = basePath;
        this.version = version;

        transactionSchema = getSchema(transactionFile);
        spanSchema = getSchema(spanFile);
        errorSchema = getSchema(errorFile);
    }

    /**
     * @return schema version, if {@literal null} should assume that it's the latest version
     */
    @Nullable
    public Version getVersion() {
        return version;
    }

    public JsonSchema transactionSchema() {
        return transactionSchema;
    }

    public JsonSchema spanSchema() {
        return spanSchema;
    }

    public JsonSchema errorSchema() {
        return errorSchema;
    }

    private JsonSchema getSchema(String resource) {
        String path = getResourcePath(resource);
        ClassLoader classLoader = JsonSchemaVersion.class.getClassLoader();
        InputStream input = classLoader.getResourceAsStream(path);
        assertThat(input)
            .describedAs("unable to load schema resource %s", path)
            .isNotNull();

        // rewrite $id and $ref fields on the fly to make validator happy
        input = rewriteSchema(path, input);

        return JsonSchemaFactory
            .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
            .uriFetcher(uri -> {
                // loads & rewrite on-the-fly schemas loaded through $ref
                if ("resource".equals(uri.getScheme())) {
                    String resourcePath = uri.getSchemeSpecificPart();
                    InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
                    Objects.requireNonNull(inputStream, "unable to load resource " + resourcePath);
                    return rewriteSchema(resourcePath, inputStream);
                }
                return uri.toURL().openStream();
            }, "resource")
            .build()
            .getSchema(input);
    }

    @NotNull
    private InputStream rewriteSchema(String path, InputStream input) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            ObjectNode jsonSchema = (ObjectNode) objectMapper.readTree(input);
            String id = jsonSchema.get(ID).asText(null);
            if (id != null && id.startsWith(SERVER_SPEC_PATH_PREFIX)) {
                jsonSchema.put(ID, String.format("resource:%s", path));
            }

            rewriteRef(jsonSchema, path);

            ByteArrayOutputStream rewriteOutput = new ByteArrayOutputStream();
            objectMapper.writeValue(rewriteOutput, jsonSchema);
            input = new ByteArrayInputStream(rewriteOutput.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return input;
    }

    private void rewriteRef(JsonNode node, String path) {
        if (node.isObject()) {
            if (node.has(REF)) {
                String ref = node.get(REF).asText();

                if (ref.startsWith(SERVER_SPEC_PATH_PREFIX)) {
                    // resolve against base path
                    ref = getResourcePath(ref.substring(SERVER_SPEC_PATH_PREFIX.length()));
                } else {
                    // resolve relative path
                    ref = resolveRelativePath(ref, path);
                }
                ((ObjectNode) node).put(REF, String.format("resource:%s", ref));
            } else {
                // rewrite all object properties recursively
                node.fields().forEachRemaining(e -> rewriteRef(e.getValue(), path));
            }
        } else if (node.isArray()) {
            // rewrite array entries recursive
            node.forEach(n -> rewriteRef(n, path));
        }
    }

    @NotNull
    private String resolveRelativePath(String urn, String resource) {
        Deque<String> path = new ArrayDeque<>(Arrays.asList(resource.split("/")));
        path.removeLast();
        for (String pathPart : urn.split("/")) {
            if ("..".equals(pathPart)) {
                path.removeLast();
            } else if (!".".equals(pathPart)) {
                path.addLast(pathPart);
            }
        }
        return String.join("/", path);
    }

    private String getResourcePath(String resource) {
        return String.format("%s/%s", basePath, resource);
    }
}
