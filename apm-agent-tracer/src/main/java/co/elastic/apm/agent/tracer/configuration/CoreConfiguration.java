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
package co.elastic.apm.agent.tracer.configuration;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public interface CoreConfiguration {

    boolean isInstrumentationEnabled(String instrumentationGroupName);

    boolean isInstrumentationEnabled(Collection<String> instrumentationGroupNames);

    boolean isCaptureHeaders();

    EventType getCaptureBody();

    String getServiceName();

    @Nullable
    String getServiceVersion();

    List<Matcher> getSanitizeFieldNames();

    long getSpanMinDurationMs();

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
