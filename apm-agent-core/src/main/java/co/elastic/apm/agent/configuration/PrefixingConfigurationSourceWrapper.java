/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.source.ConfigurationSource;

import java.io.IOException;

public class PrefixingConfigurationSourceWrapper implements ConfigurationSource {
    private final ConfigurationSource delegate;
    private final String prefix;

    public PrefixingConfigurationSourceWrapper(ConfigurationSource delegate, String prefix) {
        this.delegate = delegate;
        this.prefix = prefix;
    }

    @Override
    public String getValue(String key) {
        return delegate.getValue(prefix + key);
    }

    @Override
    public void reload() throws IOException {
        delegate.reload();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isSavingPossible() {
        return delegate.isSavingPossible();
    }

    @Override
    public boolean isSavingPersistent() {
        return delegate.isSavingPersistent();
    }

    @Override
    public void save(String key, String value) throws IOException {
        delegate.save(prefix + key, value);
    }
}
