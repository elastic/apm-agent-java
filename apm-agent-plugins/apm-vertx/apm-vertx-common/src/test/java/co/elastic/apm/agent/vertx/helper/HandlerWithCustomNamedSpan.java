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
package co.elastic.apm.agent.vertx.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class HandlerWithCustomNamedSpan implements Handler<Void> {

    private final RoutingContext routingContext;
    private final Handler<RoutingContext> handler;
    private final String spanName;

    public HandlerWithCustomNamedSpan(Handler<RoutingContext> handler, RoutingContext routingContext, String spanName) {
        this.routingContext = routingContext;
        this.spanName = spanName;
        this.handler = handler;
    }

    @Override
    public void handle(Void v) {
        AbstractSpan<?> active = GlobalTracer.get().require(ElasticApmTracer.class).getActive();
        if (active == null) {
            return;
        }
        Span child = active.createSpan();
        child.withName(spanName + "-child-span");
        child.activate();
        handler.handle(routingContext);
        child.deactivate().end();
    }
}
