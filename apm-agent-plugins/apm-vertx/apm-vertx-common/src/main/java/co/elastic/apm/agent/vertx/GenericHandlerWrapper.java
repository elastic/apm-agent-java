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

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import io.vertx.core.Handler;

public class GenericHandlerWrapper<T> implements Handler<T> {

    protected final Handler<T> actualHandler;
    private final AbstractSpan<?> parentSpan;

    public GenericHandlerWrapper(AbstractSpan<?> parentSpan, Handler<T> actualHandler) {
        this.parentSpan = parentSpan;
        this.actualHandler = actualHandler;
        parentSpan.incrementReferences();
    }

    @Override
    public void handle(T event) {
        parentSpan.activate();
        parentSpan.decrementReferences();
        try {
            actualHandler.handle(event);
        } catch (Throwable throwable) {
            parentSpan.captureException(throwable);
            throw throwable;
        } finally {
            parentSpan.deactivate();
        }
    }

    public static <T> Handler<T> wrapIfActiveSpan(Handler<T> handler) {
        AbstractSpan<?> currentSpan = GlobalTracer.get().getActive();

        if (currentSpan != null) {
            handler = new GenericHandlerWrapper<>(currentSpan, handler);
        }

        return handler;
    }
}
