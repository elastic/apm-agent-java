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
package co.elastic.apm.agent.opentelemetry.context;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.opentelemetry.sdk.OTelBridgeContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class OTelContextStorage implements ContextStorage {

    private static final Logger logger = LoggerFactory.getLogger(OTelContextStorage.class);

    private final ElasticApmTracer tracer;

    public OTelContextStorage(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Scope attach(@Nullable Context toAttach) {
        if (toAttach == null) {
            // no context to attach
            return Scope.noop();
        }

        if (toAttach == tracer.currentContext()) {
            // already active
            return Scope.noop();
        }

        if (!(toAttach instanceof OTelBridgeContext)) {
            // likely to be triggered when trying to activate a context created before agent attachment and
            // instrumentation.
            logger.debug("unexpected context type for attachment {}", toAttach.getClass().getName());
            return Scope.noop();
        }
        OTelBridgeContext bridgeContext = (OTelBridgeContext) toAttach;

        tracer.activate(bridgeContext);
        return bridgeContext;
    }

    @Nullable
    @Override
    public Context current() {
        ElasticContext<?> current = tracer.currentContext();
        if (current == null) {
            return null;
        }

        if (current instanceof OTelBridgeContext) {
            return (Context) current;
        }

        if (!(current instanceof AbstractSpan)) {
            throw new IllegalStateException("unexpected context type to upgrade: " + current.getClass().getName());
        }

        logger.debug("upgrading active context {} to a bridged context", current);

        tracer.deactivate(current);

        // Ensure that root context is being accessed at least once to capture the original root
        // OTel 1.0 directly calls ArrayBasedContext.root(), whereas later versions delegate to ContextStorage.root().
        Context.root();

        OTelBridgeContext upgradedContext = OTelBridgeContext.wrapElasticActiveSpan(tracer, (AbstractSpan<?>) current);
        tracer.activate(upgradedContext);

        logger.debug("active context upgraded to {}", upgradedContext);

        return upgradedContext;
    }
}
