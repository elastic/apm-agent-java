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
package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.GlobalTracer;
import io.vertx.core.Handler;

public class SetTimerWrapper extends GenericHandlerWrapper<Long> {

    /**
     * Use this thread local to prevent tracking of endless, recursive timer jobs
     */
    private static final ThreadLocal<String> activeTimerHandlerPerThread = new ThreadLocal<>();

    @Override
    public void handle(Long event) {
        activeTimerHandlerPerThread.set(actualHandler.getClass().getName());
        try {
            super.handle(event);
        } finally {
            activeTimerHandlerPerThread.remove();
        }
    }

    public SetTimerWrapper(ElasticContext<?> parentContext, Handler<Long> actualHandler) {
        super(parentContext, actualHandler);
    }

    public static Handler<Long> wrapTimerIfNonEmptyContext(Handler<Long> handler) {
        ElasticContext<?> current = GlobalTracer.get().currentContext();

        // do not wrap if there is no parent span or if we are in the recursive context of the same type of timer
        if (!current.isEmpty() && !handler.getClass().getName().equals(activeTimerHandlerPerThread.get())) {
            handler = new SetTimerWrapper(current, handler);
        }

        return handler;
    }

}
