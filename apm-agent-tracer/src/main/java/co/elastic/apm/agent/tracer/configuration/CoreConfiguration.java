package co.elastic.apm.agent.tracer.configuration;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface CoreConfiguration {

    boolean isInstrumentationEnabled(String instrumentationGroupName);

    boolean isInstrumentationEnabled(Collection<String> instrumentationGroupNames);

    boolean isCaptureHeaders();

    EventType getCaptureBody();

    String getServiceName();

    @Nullable
    String getServiceVersion();

    List<Matcher> getSanitizeFieldNames();

    long getSpanMinDuration(TimeUnit unit);

    boolean isEnablePublicApiAnnotationInheritance();

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

    enum CloudProvider {
        AUTO,
        AWS,
        GCP,
        AZURE,
        NONE
    }

    enum TraceContinuationStrategy {
        CONTINUE,
        RESTART,
        RESTART_EXTERNAL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
