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
package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import static co.elastic.apm.agent.webflux.client.WebSocketClientInstrumentation.APM_PARENT_SPAN;

public class WebSocketHandlerWrapper implements WebSocketHandler {

    public static final Logger logger = LoggerFactory.getLogger(WebSocketHandlerWrapper.class);
    private final Tracer tracer;
    private final WebSocketHandler actual;
    private AbstractSpan parentSpan;

    public WebSocketHandlerWrapper(WebSocketHandler actual, AbstractSpan parentSpan, Tracer tracer) {
        this.actual = actual;
        this.parentSpan = parentSpan;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String logPrefix = session.getId();

        if (logPrefix != null && parentSpan != null) {
            WebfluxClientSubscriber.getLogPrefixMap().put(logPrefix, parentSpan);
            session.getAttributes().put(APM_PARENT_SPAN, logPrefix);

            return (Mono<Void>)WebfluxClientHelper.wrapSubscriber((Publisher) actual.handle(session), logPrefix, tracer,
                "WebSocketHandlerHandle-");
        } else {
            return actual.handle(session);
        }
    }

    public AbstractSpan getParentSpan() {
        return parentSpan;
    }

    public void setParentSpan(AbstractSpan parentSpan) {
        this.parentSpan = parentSpan;
    }


}
