/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.api;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandle;

public abstract class AbstractSpanImpl implements Span {
    @Nonnull
    // co.elastic.apm.impl.transaction.AbstractSpan
    protected final Object span;

    AbstractSpanImpl(@Nonnull Object span) {
        this.span = span;
    }

    @Nonnull
    @Override
    public Span createSpan() {
        Object span = doCreateSpan();
        return span != null ? new SpanImpl(span) : NoopSpan.INSTANCE;
    }

    private Object doCreateSpan() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$DoCreateSpanInstrumentation.doCreateSpan
        return null;
    }

    void doSetName(String name) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$SetNameInstrumentation.doSetName
    }

    void doSetType(String type) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$SetTypeInstrumentation.doSetType
    }

    void doAddTag(String key, String value) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$AddTagInstrumentation.doAddTag
    }

    @Override
    public void end() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$EndInstrumentation.end
    }

    @Override
    public void captureException(Throwable throwable) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.CaptureExceptionInstrumentation
    }

    @Nonnull
    @Override
    public String getId() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.GetIdInstrumentation
        return "";
    }

    @Nonnull
    @Override
    public String getTraceId() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.GetTraceIdInstrumentation
        return "";
    }

    @Override
    public Scope activate() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.ActivateInstrumentation
        return new ScopeImpl(span);
    }

    @Override
    public boolean isSampled() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.IsSampledInstrumentation
        return false;
    }

    @Override
    public void injectTraceHeaders(HeaderInjector headerInjector) {
        doInjectTraceHeaders(ApiMethodHandles.ADD_HEADER, headerInjector);
    }

    private void doInjectTraceHeaders(MethodHandle addHeader, HeaderInjector headerInjector) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.InjectTraceHeadersInstrumentation
    }
}
