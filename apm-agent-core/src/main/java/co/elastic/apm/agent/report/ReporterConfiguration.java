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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.converter.ByteValue;
import co.elastic.apm.agent.configuration.converter.ByteValueConverter;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
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
    public static final URL LOCAL_APM_SERVER_URL = UrlValueConverter.INSTANCE.convert("http://localhost:8200");

    private final ConfigurationOption<String> secretToken = ConfigurationOption.stringOption()
        .key("secret_token")
        .configurationCategory(REPORTER_CATEGORY)
        .description("This string is used to ensure that only your agents can send data to your APM server.\n" +
            "\n" +
            "Both the agents and the APM server have to be configured with the same secret token.\n" +
            "Use if APM Server requires a token.")
        .sensitive()
        .build();

    private final ConfigurationOption<String> apiKey = ConfigurationOption.stringOption()
        .key("api_key")
        .configurationCategory(REPORTER_CATEGORY)
        .description("This string is used to ensure that only your agents can send data to your APM server.\n" +
            "\n" +
            "Agents can use API keys as a replacement of secret token, APM server can have multiple API keys.\n" +
            "When both secret token and API key are used, API key has priority and secret token is ignored.\n" +
            "Use if APM Server requires an API key.")
        .sensitive()
        .build();

    private final ConfigurationOption<URL> serverUrl = ConfigurationOption.urlOption()
        .key("server_url")
        .configurationCategory(REPORTER_CATEGORY)
        .label("The URL for your APM Server")
        .description("The URL must be fully qualified, including protocol (http or https) and port.\n" +
            "\n" +
            "If SSL is enabled on the APM Server, use the `https` protocol. For more information, see \n" +
            "<<ssl-configuration>>.\n" +
            "\n" +
            "If outgoing HTTP traffic has to go through a proxy,\n" +
            "you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up.\n" +
            "See also https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html[Java's proxy documentation] \n" +
            "for more information.\n" +
            "\n" +
            "NOTE: This configuration can only be reloaded dynamically as of 1.8.0")
        .dynamic(true)
        .buildWithDefault(LOCAL_APM_SERVER_URL);

    private final ConfigurationOption<List<URL>> serverUrls = ConfigurationOption.urlsOption()
        .key("server_urls")
        .configurationCategory(REPORTER_CATEGORY)
        .label("The URLs for your APM Servers")
        .description("The URLs must be fully qualified, including protocol (http or https) and port.\n" +
            "\n" +
            "Fails over to the next APM Server URL in the event of connection errors.\n" +
            "Achieves load-balancing by shuffling the list of configured URLs.\n" +
            "When multiple agents are active, they'll tend towards spreading evenly across the set of servers due to randomization.\n" +
            "\n" +
            "If SSL is enabled on the APM Server, use the `https` protocol. For more information, see \n" +
            "<<ssl-configuration>>.\n" +
            "\n" +
            "If outgoing HTTP traffic has to go through a proxy,\n" +
            "you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up.\n" +
            "See also https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html[Java's proxy documentation] \n" +
            "for more information.\n" +
            "\n" +
            "NOTE: This configuration is specific to the Java agent and does not align with any other APM agent. In order \n" +
            "to use a cross-agent config, use <<config-server-url>> instead, which is the recommended option regardless if you \n" +
            "are only setting a single URL.")
        .dynamic(true)
        .buildWithDefault(Collections.<URL>emptyList());

    private final ConfigurationOption<Boolean> disableSend = ConfigurationOption.booleanOption()
        .key("disable_send")
        .configurationCategory(REPORTER_CATEGORY)
        .description("If set to `true`, the agent will work as usual, except from any task requiring communication with \n" +
            "the APM server. Events will be dropped and the agent won't be able to receive central configuration, which \n" +
            "means that any other configuration cannot be changed in this state without restarting the service. \n" +
            "An example use case for this would be maintaining the ability to create traces and log \n" +
            "trace/transaction/span IDs through the log correlation feature, without setting up an APM Server.")
        .dynamic(false)
        .buildWithDefault(false);

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
        .dynamic(false)
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

    @Nullable
    public String getApiKey() {
        return apiKey.get();
    }

    /**
     * Provides the combined output of two config options - {@code server_url} and {@code server_urls}, with precedence
     * for the singular form. If {@link ReporterConfiguration#serverUrl} is set with an empty string and
     * {@link ReporterConfiguration#serverUrls} is not set, this method is expected to provide an empty list.
     *
     * Algorithm:
     *  1.  if the server_url value is set, then:
     *      a.  if it is set with the default value (i.e == LOCAL_APM_SERVER_URL) - look for server_urls
     *          i.  if server_urls is not empty - use it
     *          ii. otherwise - use the default
     *      b.  otherwise use it and if server_urls is not empty - log a warning
     *  2.  otherwise use the value from server_urls
     *
     * @return a list of APM Server URLs resulting from the combination of {@code server_url} and {@code server_urls}
     */
    public List<URL> getServerUrls() {
        if (disableSend.get()) {
            return Collections.emptyList();
        }
        List<URL> calculatedUrlList;
        URL singleUrl = serverUrl.get();
        List<URL> urlList = serverUrls.get();
        if (singleUrl != null) {
            if (singleUrl == LOCAL_APM_SERVER_URL) {
                if (urlList != null && !urlList.isEmpty()) {
                    calculatedUrlList = urlList;
                } else {
                    calculatedUrlList = Collections.singletonList(singleUrl);
                }
            } else {
                calculatedUrlList = Collections.singletonList(singleUrl);
                if (urlList != null && !urlList.isEmpty()) {
                    // It should be safe to get a logger at this point as it is called after LoggingConfiguration.init()
                    LoggerFactory.getLogger(getClass()).info("Both \"server_urls\" and \"server_url\" configuration options " +
                        "are set, therefore the \"server_urls\" configuration will be ignored.");
                }
            }
        } else {
            calculatedUrlList = urlList;
        }
        return calculatedUrlList;
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

    public ConfigurationOption<URL> getServerUrlOption() {
        return this.serverUrl;
    }

    public ConfigurationOption<List<URL>> getServerUrlsOption() {
        return this.serverUrls;
    }
}
