/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.vertx.helper;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
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
        Span child = ElasticApm.currentSpan().startSpan();
        child.setName(spanName + "-child-span");
        handler.handle(routingContext);
        child.end();
    }
}
