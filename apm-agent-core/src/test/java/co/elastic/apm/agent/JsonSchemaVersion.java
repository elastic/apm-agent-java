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

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.uri.ClasspathURLFactory;
import com.networknt.schema.uri.URIFetcher;
import com.networknt.schema.urn.URNFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

enum JsonSchemaVersion {

    V6_5("schema-v6.5.0",
        "transactions/v2_transaction.json",
        "spans/v2_span.json",
        "errors/v2_error.json"
    ),

    MASTER("schema-master",
        "transactions/transaction.json",
        "spans/span.json",
        "errors/error.json"),

    CURRENT("schema-current",
        "transactions/transaction.json",
        "transactions/span.json",
        "errors/error.json");

    public final JsonSchema transactionSchema;
    public final JsonSchema spanSchema;
    public final JsonSchema errorSchema;

    private final String basePath;

    JsonSchemaVersion(String basePath, String transactionFile, String spanFile, String errorFile) {
        this.basePath = basePath;

        transactionSchema = getSchema(transactionFile);
        spanSchema = getSchema(spanFile);
        errorSchema = getSchema(errorFile);
    }

    private JsonSchema getSchema(String resource) {
        String path = getResourcePath(resource);
        ClassLoader classLoader = JsonSchemaVersion.class.getClassLoader();
        InputStream input = classLoader.getResourceAsStream(path);
        assertThat(input)
            .describedAs("unable to load schema resource %s", path)
            .isNotNull();

        ClasspathURLFactory urlFactory = new ClasspathURLFactory();

        return JsonSchemaFactory
            .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4))
            .addUrnFactory(new URNFactory() {
                @Override
                public URI create(String urn) {

                    // resolve relative paths
                    String finalPath = resolveRelativePath(urn, resource);
                    String newUrn = String.format("resource:%s", getResourcePath(finalPath));
                    try {
                        URL url = ClasspathURLFactory.convert(urlFactory.create(newUrn));
                        return url.toURI();
                    } catch (MalformedURLException | URISyntaxException e) {
                        throw new IllegalStateException(e);
                    }

                }
            })
            .uriFetcher(new URIFetcher() {
                @Override
                public InputStream fetch(URI uri) throws IOException {

                    if ("resource".equals(uri.getScheme())) {
                        String path = uri.getSchemeSpecificPart();
                        if (path.startsWith("/schema/")) {
                            // make the existing in 'current' version work
                            path = uri.getPath().replaceFirst("/schema", basePath);
                        }
                        InputStream inputStream = classLoader.getResourceAsStream(path);
                        Objects.requireNonNull(inputStream, "unable to load resource " + path);
                        return inputStream;
                    }
                    return uri.toURL().openStream();
                }

            }, "resource")
            .build()
            .getSchema(input);
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
