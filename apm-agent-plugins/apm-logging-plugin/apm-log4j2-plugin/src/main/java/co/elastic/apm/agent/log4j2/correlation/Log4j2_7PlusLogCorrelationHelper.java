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
package co.elastic.apm.agent.log4j2.correlation;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.loginstr.correlation.AbstractLogCorrelationHelper;
import co.elastic.apm.agent.loginstr.correlation.CorrelationIdMapAdapter;
import co.elastic.apm.agent.tracer.Tracer;
import org.apache.logging.log4j.ThreadContext;

import java.util.Map;

/**
 * Using {@link ThreadContext#putAll(Map)} that is available since 2.7 for improved efficiency
 */
public class Log4j2_7PlusLogCorrelationHelper extends AbstractLogCorrelationHelper {

    private final Tracer tracer = GlobalTracer.get();

    @Override
    protected boolean addToMdc() {
        if (tracer.currentTransaction() == null && ErrorCapture.getActive() == null) {
            return false;
        }
        ThreadContext.putAll(CorrelationIdMapAdapter.get());
        return true;
    }

    @Override
    protected void removeFromMdc() {
        ThreadContext.removeAll(CorrelationIdMapAdapter.allKeys());
    }
}
