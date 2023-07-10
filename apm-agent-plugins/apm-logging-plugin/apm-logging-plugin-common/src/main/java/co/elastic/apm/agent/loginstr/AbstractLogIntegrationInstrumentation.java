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
package co.elastic.apm.agent.loginstr;


import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractLogIntegrationInstrumentation extends ElasticApmInstrumentation {

    protected static String LOG_CORRELATION = "log-correlation";
    protected static String LOG_REFORMATTING = "log-reformatting";
    protected static String LOG_ERROR = "log-error";

    protected static final Tracer tracer = GlobalTracer.get();

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("logging", getLoggingInstrumentationGroupName());
    }

    protected abstract String getLoggingInstrumentationGroupName();

}
