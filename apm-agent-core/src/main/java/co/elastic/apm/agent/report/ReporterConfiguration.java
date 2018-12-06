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
package co.elastic.apm.report;

import co.elastic.apm.configuration.converter.ByteValue;
import co.elastic.apm.configuration.converter.ByteValueConverter;
import co.elastic.apm.configuration.converter.TimeDuration;
import co.elastic.apm.configuration.converter.TimeDurationValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.UrlValueConverter;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collections;
import java.util.List;

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
            "NOTE: Providing multiple URLs only works if intake API v2 is enabled.")
        .dynamic(false)
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
        .buildWithDefault(TimeDuration.of("5s"));

    private final ConfigurationOption<Boolean> verifyServerCert = ConfigurationOption.booleanOption()
        .key("verify_server_cert")
        .configurationCategory(REPORTER_CATEGORY)
        .description("By default, the agent verifies the SSL certificate if you use an HTTPS connection to the APM server.\n" +
            "\n" +
            "Verification can be disabled by changing this setting to false.")
        .buildWithDefault(true);

    private final ConfigurationOption<TimeDuration> flushInterval = TimeDurationValueConverter.durationOption("s")
        .key("flush_interval")
        .configurationCategory(REPORTER_CATEGORY)
        .description("Interval with which transactions should be sent to the APM server.\n" +
            "\n" +
            "A lower value will increase the load on your APM server,\n" +
            "while a higher value can increase the memory pressure on your app.\n" +
            "\n" +
            "A higher value also impacts the time until transactions are indexed and searchable in Elasticsearch.")
        .buildWithDefault(TimeDuration.of("1s"));

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
        .description("Maximum time to keep an HTTP request to the APM Server open for.")
        .buildWithDefault(TimeDuration.of("10s"));

    private final ConfigurationOption<ByteValue> apiRequestSize = ByteValueConverter.byteOption()
        .key("api_request_size")
        .configurationCategory(REPORTER_CATEGORY)
        .description("The maximum total compressed size of the request body which is sent to the APM server intake api via a " +
            "chunked encoding (HTTP streaming).\n" +
            "Note that a small overshoot is possible.\n" +
            "\n" +
            "Allowed byte units are `b`, `kb` and `mb`. `1kb` is equal to `1024b`.")
        .buildWithDefault(ByteValue.of("768kb"));

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

    public TimeDuration getFlushInterval() {
        return flushInterval.get();
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
}
