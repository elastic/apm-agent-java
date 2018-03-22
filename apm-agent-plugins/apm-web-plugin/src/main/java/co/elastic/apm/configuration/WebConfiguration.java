package co.elastic.apm.configuration;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class WebConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<EventType> captureBody = ConfigurationOption.enumOption(EventType.class)
        .key("capture_body")
        .description("For transactions that are HTTP requests, the Java agent can optionally capture the request body (e.g. POST " +
            "variables).\n" +
            "\n" +
            "Possible values: errors, transactions, all, off.\n" +
            "\n" +
            "If the request has a body and this setting is disabled, the body will be shown as [REDACTED].\n" +
            "\n" +
            "For requests with a content type of multipart/form-data, any uploaded files will be referenced in a special _files key. It " +
            "contains the name of the field, and the name of the uploaded file, if provided.\n" +
            "\n" +
            "WARNING: request bodies often contain sensitive values like passwords, credit card numbers etc." +
            "If your service handles data like this, we advise to only enable this feature with care.")
        .dynamic(true)
        .buildWithDefault(EventType.OFF);

    private final ConfigurationOption<Collection<String>> ignoreUrlsStartingWith = ConfigurationOption.stringsOption()
        .key("ignore_urls_starting_with")
        .description("Used to restrict requests to certain URL’s from being instrumented.\n" +
            "\n" +
            "This property should be set to an array containing one or more strings. " +
            "When an incoming HTTP request is detected, its URL will be tested against each element in this list. " +
            "If the URL starts with an element in the array. " +
            "\n" +
            "Note that all errors that are captured during a request to an ignored URL are still sent to the APM Server regardless of this setting.")
        .dynamic(true)
        .buildWithDefault(Collections.<String>emptyList());

    public EventType getCaptureBody() {
        return captureBody.get();
    }

    public Collection<String> getIgnoreUrlsStartingWith() {
        return ignoreUrlsStartingWith.get();
    }

    public enum EventType {
        /**
         * Request bodies will never be reported
         */
        OFF,
        /**
         * Request bodies will only be reported with errors
         */
        ERRORS,
        /**
         * Request bodies will only be reported with request transactions
         */
        TRANSACTIONS,
        /**
         * Request bodies will be reported with both errors and request transactions
         */
        ALL
    }
}
