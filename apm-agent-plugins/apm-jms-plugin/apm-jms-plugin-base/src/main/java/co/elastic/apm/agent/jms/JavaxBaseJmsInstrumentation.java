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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;

public abstract class JavaxBaseJmsInstrumentation extends BaseJmsInstrumentation {

    @Override
    public String rootClassNameThatClassloaderCanLoad() {
        return "javax.jms.Message";
    }

    protected static class JavaxBaseAdvice extends BaseAdvice {

        protected static final JavaxJmsInstrumentationHelper helper;

        static {
            ElasticApmTracer elasticApmTracer = GlobalTracer.requireTracerImpl();

            // loading helper class will load JMS-related classes if loaded from Instrumentation static init
            // that fails when trying to load instrumentation classes without JMS dependencies, for example when generating
            // documentation that relies on instrumentation group names
            helper = new JavaxJmsInstrumentationHelper(elasticApmTracer);
        }
    }
}
