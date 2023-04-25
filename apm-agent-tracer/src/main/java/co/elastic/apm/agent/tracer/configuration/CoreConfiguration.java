package co.elastic.apm.agent.tracer.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.ActivationMethod;

import java.util.Collection;
import java.util.List;

public interface CoreConfiguration {

    boolean isInstrumentationEnabled(String instrumentationGroupName);

    boolean isInstrumentationEnabled(Collection<String> instrumentationGroupNames);

    boolean isCaptureHeaders();

    EventType getCaptureBody();

    boolean isEnablePublicApiAnnotationInheritance();

    List<WildcardMatcher> getSanitizeFieldNames();

    String getServiceName();

    String getServiceVersion();

    String getServiceNodeName();

    String getEnvironment();

    ActivationMethod getActivationMethod();

    TimeDuration getSpanMinDuration();

    enum EventType {
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
        ALL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
