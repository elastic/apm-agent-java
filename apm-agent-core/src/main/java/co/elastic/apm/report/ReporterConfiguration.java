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
package co.elastic.apm.report;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.UrlValueConverter;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ReporterConfiguration extends ConfigurationOptionProvider {
    public static final String REPORTER_CATEGORY = "Reporter";
    private final ConfigurationOption<String> secretToken = ConfigurationOption.stringOption()
        .key("secret_token")
        .configurationCategory(REPORTER_CATEGORY)
        .description("This string is used to ensure that only your agents can send data to your APM server.\n\n" +
            "Both the agents and the APM server have to be configured with the same secret token." +
            "Use if APM Server requires a token.")
        .sensitive()
        .build();

    private final ConfigurationOption<URL> serverUrl = ConfigurationOption.urlOption()
        .key("server_url")
        .configurationCategory(REPORTER_CATEGORY)
        .label("The URL for your APM Server")
        .description("The URL must be fully qualified, including protocol (http or https) and port.")
        .buildWithDefault(UrlValueConverter.INSTANCE.convert("http://localhost:8200"));

    private final ConfigurationOption<Integer> serverTimeout = ConfigurationOption.integerOption()
        .key("server_timeout")
        .configurationCategory(REPORTER_CATEGORY)
        .label("A timeout in seconds.")
        .description("If a request to the APM server takes longer than the configured timeout, " +
            "the request is cancelled and the event (exception or transaction) is discarded. Set to None to disable timeouts.\n" +
            "\n" +
            "WARNING:  If timeouts are disabled or set to a high value, your app could experience memory issues if the APM server times " +
            "out.")
        .buildWithDefault(5);

    private final ConfigurationOption<Boolean> verifyServerCert = ConfigurationOption.booleanOption()
        .key("verify_server_cert")
        .configurationCategory(REPORTER_CATEGORY)
        .description("By default, the agent verifies the SSL certificate if you use an HTTPS connection to the APM server.\n\n" +
            "Verification can be disabled by changing this setting to false.")
        .buildWithDefault(true);

    private final ConfigurationOption<Integer> flushInterval = ConfigurationOption.integerOption()
        .key("flush_interval")
        .configurationCategory(REPORTER_CATEGORY)
        .description("Interval with which transactions should be sent to the APM server, in seconds.\n\n" +
            "A lower value will increase the load on your APM server, " +
            "while a higher value can increase the memory pressure on your app.\n\n" +
            "A higher value also impacts the time until transactions are indexed and searchable in Elasticsearch.")
        .buildWithDefault(1);

    private final ConfigurationOption<Integer> maxQueueSize = ConfigurationOption.integerOption()
        .key("max_queue_size")
        .configurationCategory(REPORTER_CATEGORY)
        .description("Maximum queue length of transactions before sending transactions to the APM server.\n\n" +
            "A lower value will increase the load on your APM server," +
            "while a higher value can increase the memory pressure of your app.\n\n" +
            "A higher value also impacts the time until transactions are indexed and searchable in Elasticsearch.\n\n" +
            "This setting is useful to limit memory consumption if you experience a sudden spike of traffic.")
        .dynamic(true)
        .buildWithDefault(500);

    @Nullable
    public String getSecretToken() {
        return secretToken.get();
    }

    public String getServerUrl() {
        return serverUrl.getValueAsString();
    }

    public int getServerTimeout() {
        return serverTimeout.get();
    }

    public boolean isVerifyServerCert() {
        return verifyServerCert.get();
    }

    public int getFlushInterval() {
        return flushInterval.get();
    }

    public int getMaxQueueSize() {
        return maxQueueSize.get();
    }
}
