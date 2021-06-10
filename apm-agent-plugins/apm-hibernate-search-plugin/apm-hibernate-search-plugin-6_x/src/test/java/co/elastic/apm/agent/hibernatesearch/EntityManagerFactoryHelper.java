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
package co.elastic.apm.agent.hibernatesearch;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class EntityManagerFactoryHelper {

    public static EntityManagerFactory buildEntityManagerFactory(final Path tempDirectory) {
        Map<String, Object> configOverrides = new HashMap<>();
        configOverrides.put("hibernate.search.backend.testBackend.type", "lucene");
        configOverrides.put("hibernate.search.backend.testBackend.directory.type", "local-filesystem");
        configOverrides.put("hibernate.search.backend.directory.root", tempDirectory.toAbsolutePath().toString());
        configOverrides.put("hibernate.search.default_backend", "testBackend");
        return Persistence.createEntityManagerFactory("templatePU", configOverrides);
    }
}
