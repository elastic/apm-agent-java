/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

enum NoopSpan implements Span {
    INSTANCE;

    @Nonnull
    @Override
    public Span setName(String name) {
        // noop
        return this;
    }

    @Nonnull
    @Override
    public Span setType(String type) {
        // noop
        return this;
    }

    @Nonnull
    @Override
    public Span addTag(String key, String value) {
        // noop
        return this;
    }

    @Override
    public void end() {
        // noop
    }

    @Override
    public void captureException(Throwable throwable) {
        // co.elastic.apm.agent.plugin.api.CaptureExceptionInstrumentation
    }

    @Nonnull
    @Override
    public String getId() {
        return "";
    }

    @Nonnull
    @Override
    public String getTraceId() {
        return "";
    }

    @Override
    public Scope activate() {
        return NoopScope.INSTANCE;
    }

    @Nonnull
    @Override
    public boolean isSampled() {
        return false;
    }

    @Override
    public Span createSpan() {
        return INSTANCE;
    }

    @Override
    public void addTraceHeaders(@Nullable Map<? super String, ? super String> headers) {
        // noop
    }

    @Nonnull
    @Override
    public Map<String, String> getTraceHeaders() {
        return Collections.emptyMap();
    }
}
