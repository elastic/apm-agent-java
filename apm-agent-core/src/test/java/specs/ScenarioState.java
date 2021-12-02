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
package specs;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.Reporter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;
import wiremock.com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Used to share state between steps.
 * See https://github.com/cucumber/cucumber-jvm/tree/main/picocontainer
 */
public class ScenarioState {

    private static final String CONFIG_SOURCE_NAME = "Cucumber-config-source";

    @Nullable
    private ElasticApmTracer tracer;

    @Nullable
    private JsonNode apmServerResponse;

    private Transaction transaction;
    private Span span;

    public ElasticApmTracer getTracer() {
        return Objects.requireNonNull(tracer);
    }

    public void initTracer(Map<String, String> configOptions) {
        if (tracer != null) {
            throw new IllegalStateException("Cannot use both \"Given an agent...\" within the same scenario");
        }
        final SimpleSource configSource = new SimpleSource(CONFIG_SOURCE_NAME);
        configOptions.forEach((key, value) -> {
            if (!key.equals("setting")) {
                configSource.add(key, value);
            }
        });
        ConfigurationRegistry.Builder builder = ConfigurationRegistry.builder();
        for (ConfigurationOptionProvider options : ServiceLoader.load(ConfigurationOptionProvider.class)) {
            builder.addOptionProvider(options);
        }
        final ConfigurationRegistry configurationRegistry = builder.addConfigSource(configSource).build();
        configOptions.forEach((key, value) -> {
            if (!key.equals("setting")) {
                ConfigurationOption<?> configOption = configurationRegistry.getConfigurationOptionByKey(key);
                System.out.println(String.format("Value of config option `%s` is set to `%s`", configOption.getKey(), configOption.getValueAsString()));
            }
        });
        tracer = MockTracer.createRealTracer(mock(Reporter.class), configurationRegistry);
    }

    /**
     * Can be used to set a dynamic configuration option. Trying to use this method to set a non-dynamic config option
     * will result with an error. Instead, use {@link #initTracer(Map)} to set non-dynamic config options.
     * @param configOptionKey config option key
     * @param value the String representation of the requested new config option value
     * @throws IOException
     */
    public void setConfigOption(String configOptionKey, String value) throws IOException {
        ConfigurationRegistry config = getTracer().getConfigurationRegistry();
        ConfigurationOption configOption = config.getConfigurationOptionByKey(configOptionKey);
        Object convertedValue = configOption.getValueConverter().convert(value);
        configOption.update(convertedValue, CONFIG_SOURCE_NAME);
    }

    public JsonNode getApmServerResponse() {
        return Objects.requireNonNull(apmServerResponse);
    }

    public void setApmServerResponse(JsonNode apmServerResponse) {
        this.apmServerResponse = apmServerResponse;
    }

    public void startRootTransactionIfRequired() {
        if (transaction == null) {
            startTransaction();
        }
    }

    public Transaction startTransaction() {
        if (transaction != null) {
            transaction.end();
        }
        transaction = getTracer().startRootTransaction(getClass().getClassLoader());

        return transaction;
    }

    public Span startSpan() {
        if (span != null) {
            span.end();
        }

        assertThat(transaction)
            .describedAs("transaction required to create span")
            .isNotNull();

        span = transaction.createSpan();
        return span;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Span getSpan() {
        return span;
    }

    /**
     * Gets the context corresponding the provided argument based on type (span/transaction)
     * @param contextType type name of the required context
     * @return the current span or transaction, based on the provided type name
     */
    public AbstractSpan<?> getContext(String contextType) {
        return contextType.equals("span") ? getSpan() : getTransaction();
    }
}
