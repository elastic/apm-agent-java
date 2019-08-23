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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.converter.ByteValue;
import co.elastic.apm.agent.configuration.converter.ByteValueConverter;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;
import org.stagemonitor.configuration.converter.UrlValueConverter;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.configuration.validation.RangeValidator.isNotInRange;

public class ReporterConfiguration extends ConfigurationOptionProvider {
    public static final String REPORTER_CATEGORY = "Reporter";
    private final ConfigurationOption<String> secretToken = ConfigurationOption.stringOption()
        .key("secret_token")
        .configurationCategory(REPORTER_CATEGORY)
        .description("This string is used to ensure that only your agents can send data to your APM server.\n" +
            "\n" +
            "Both the agents and the APM server have to be configured with the same secret token.\n" +
            "Use if APM Server requires a token.")
        .sensitive()
        .build();

    private final ConfigurationOption<List<URL>> serverUrl = ConfigurationOption.urlsOption()
        .key("server_urls")
        .aliasKeys("server_url")
        .configurationCategory(REPORTER_CATEGORY)
        .label("The URLs for your APM Servers")
        .description("The URLs must be fully qualified, including protocol (http or https) and port.\n" +
            "\n" +
            "Fails over to the next APM Server URL in the event of connection errors.\n" +
            "Achieves load-balancing by shuffling the list of configured URLs.\n" +
            "When multiple agents are active, they'll tend towards spreading evenly across the set of servers due to randomization.\n" +
            "\n" +
            "If outgoing HTTP traffic has to go through a proxy," +
            "you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up.\n" +
            "See also [Java's proxy documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html) for more information.\n" +
            "\n" +
            "NOTE: This configuration can only be reloaded dynamically as of 1.8.0")
        .dynamic(true)
        .buildWithDefault(Collections.singletonList(UrlValueConverter.INSTANCE.convert("http://localhost:8200")));

    private final ConfigurationOption<TimeDuration> serverTimeout = TimeDurationValueConverter.durationOption("s")
        .key("server_timeout")
        .configurationCategory(REPORTER_CATEGORY)
        .label("Server timeout")
        .description("If a request to the APM server takes longer than the configured timeout,\n" +
            "the request is cancelled and the event (exception or transaction) is discarded.\n" +
            "Set to 0 to disable timeouts.\n" +
            "\n" +
            "WARNING: If timeouts are disabled or set to a high value, your app could experience memory issues if the APM server times " +
            "out.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("5s"));

    private final ConfigurationOption<Boolean> verifyServerCert = ConfigurationOption.booleanOption()
        .key("verify_server_cert")
        .configurationCategory(REPORTER_CATEGORY)
        .description("By default, the agent verifies the SSL certificate if you use an HTTPS connection to the APM server.\n" +
            "\n" +
            "Verification can be disabled by changing this setting to false.")
        .buildWithDefault(true);

    private final ConfigurationOption<Integer> maxQueueSize = ConfigurationOption.integerOption()
        .key("max_queue_size")
        .configurationCategory(REPORTER_CATEGORY)
        .description("The maximum size of buffered events.\n" +
            "\n" +
            "Events like transactions and spans are buffered when the agent can't keep up with sending them to the APM Server " +
            "or if the APM server is down.\n" +
            "\n" +
            "If the queue is full, events are rejected which means you will lose transactions and spans in that case.\n" +
            "This guards the application from crashing in case the APM server is unavailable for a longer period of time.\n" +
            "\n" +
            "A lower value will decrease the heap overhead of the agent,\n" +
            "while a higher value makes it less likely to lose events in case of a temporary spike in throughput.")
        .dynamic(true)
        .buildWithDefault(512);

    private final ConfigurationOption<Boolean> reportSynchronously = ConfigurationOption.booleanOption()
        .key("report_sync")
        .tags("internal")
        .configurationCategory(REPORTER_CATEGORY)
        .description("Only to be used for testing purposes. " +
            "Blocks the requests until the transaction has been reported to the APM server.")
        .buildWithDefault(false);

    private final ConfigurationOption<Boolean> includeProcessArguments = ConfigurationOption.booleanOption()
        .key("include_process_args")
        .configurationCategory(REPORTER_CATEGORY)
        .description("Whether each transaction should have the process arguments attached.\n" +
            "Disabled by default to save disk space.")
        .buildWithDefault(false);

    private final ConfigurationOption<TimeDuration> apiRequestTime = TimeDurationValueConverter.durationOption("s")
        .key("api_request_time")
        .configurationCategory(REPORTER_CATEGORY)
        .dynamic(true)
        .description("Maximum time to keep an HTTP request to the APM Server open for.\n" +
            "\n" +
            "NOTE: This value has to be lower than the APM Server's `read_timeout` setting.")
        .buildWithDefault(TimeDuration.of("10s"));

    private final ConfigurationOption<ByteValue> apiRequestSize = ByteValueConverter.byteOption()
        .key("api_request_size")
        .configurationCategory(REPORTER_CATEGORY)
        .dynamic(true)
        .description("The maximum total compressed size of the request body which is sent to the APM server intake api via a " +
            "chunked encoding (HTTP streaming).\n" +
            "Note that a small overshoot is possible.\n" +
            "\n" +
            "Allowed byte units are `b`, `kb` and `mb`. `1kb` is equal to `1024b`.")
        .buildWithDefault(ByteValue.of("768kb"));

    private final ConfigurationOption<TimeDuration> metricsInterval = TimeDurationValueConverter.durationOption("s")
        .key("metrics_interval")
        .tags("added[1.3.0]")
        .configurationCategory(REPORTER_CATEGORY)
        .description("The interval at which the agent sends metrics to the APM Server.\n" +
            "Must be at least `1s`.\n" +
            "Set to `0s` to deactivate.")
        .addValidator(isNotInRange(TimeDuration.of("1ms"), TimeDuration.of("999ms")))
        .buildWithDefault(TimeDuration.of("30s"));

    private final ConfigurationOption<List<WildcardMatcher>> disableMetrics = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("disable_metrics")
        .tags("added[1.3.0]")
        .configurationCategory(REPORTER_CATEGORY)
        .description("Disables the collection of certain metrics.\n" +
            "If the name of a metric matches any of the wildcard expressions, it will not be collected.\n" +
            "Example: `foo.*,bar.*`\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(false)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    @Nullable
    public String getSecretToken() {
        return secretToken.get();
    }

    public List<URL> getServerUrls() {
        return serverUrl.get();
    }

    public TimeDuration getServerTimeout() {
        return serverTimeout.get();
    }

    public boolean isVerifyServerCert() {
        return verifyServerCert.get();
    }

    public int getMaxQueueSize() {
        return maxQueueSize.get();
    }

    public boolean isReportSynchronously() {
        return reportSynchronously.get();
    }

    public boolean isIncludeProcessArguments() {
        return includeProcessArguments.get();
    }

    public TimeDuration getApiRequestTime() {
        return apiRequestTime.get();
    }

    public long getApiRequestSize() {
        return apiRequestSize.get().getBytes();
    }

    public long getMetricsIntervalMs() {
        return metricsInterval.get().getMillis();
    }

    public List<WildcardMatcher> getDisableMetrics() {
        return disableMetrics.get();
    }

    public ConfigurationOption<List<URL>> getServerUrlsOption() {
        return this.serverUrl;
    }

}
