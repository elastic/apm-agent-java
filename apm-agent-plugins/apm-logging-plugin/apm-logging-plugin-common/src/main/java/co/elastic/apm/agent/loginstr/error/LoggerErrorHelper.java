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
package co.elastic.apm.agent.loginstr.error;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.util.PrivilegedActionUtils;

import javax.annotation.Nullable;

public class LoggerErrorHelper {

    private final CallDepth callDepth;
    private final Tracer tracer;

    public LoggerErrorHelper(Class<?> adviceClass, Tracer tracer) {
        this.callDepth = CallDepth.get(adviceClass);
        this.tracer = tracer;
    }

    /**
     * Start error capture and make error active. Must be called even if `exception` is null for proper nested calls detection.
     *
     * @param exception   exception to capture
     * @param originClass origin class
     * @return error capture, if any, {@literal null} for nested calls and when no exception provided.
     */
    @Nullable
    public Object enter(@Nullable Throwable exception, Class<?> originClass) {
        if (!callDepth.isNestedCallAndIncrement()) {
            if (exception != null) {
                co.elastic.apm.agent.impl.Tracer required = tracer.require(co.elastic.apm.agent.impl.Tracer.class);
                ErrorCapture error = required.captureException(exception, required.getActive(), PrivilegedActionUtils.getClassLoader(originClass));
                if (error != null) {
                    error.activate();
                }
                return error;
            }
        }
        return null;
    }

    /**
     * End error capture and de-activate error. Must be called even if `exception` is null for proper nested calls detection
     *
     * @param errorCapture value returned by {@link #enter(Throwable, Class)}
     */
    public void exit(@Nullable Object errorCapture) {
        callDepth.decrement();
        if (errorCapture instanceof ErrorCapture) {
            ErrorCapture error = (ErrorCapture) errorCapture;
            error.deactivate().end();
        }
    }
}
