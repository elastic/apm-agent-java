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

    boolean isAvoidTouchingExceptions();

    boolean isUseServletAttributesForExceptionPropagation();

    boolean isCaptureThreadOnStart();

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
