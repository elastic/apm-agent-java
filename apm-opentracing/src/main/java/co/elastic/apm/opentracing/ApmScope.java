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
package co.elastic.apm.opentracing;

import io.opentracing.Scope;

import javax.annotation.Nullable;
import java.util.Objects;

class ApmScope implements Scope {

    private final boolean finishSpanOnClose;
    private final ApmSpan apmSpan;

    ApmScope(boolean finishSpanOnClose, ApmSpan apmSpan) {
        this.finishSpanOnClose = finishSpanOnClose;
        this.apmSpan = apmSpan;
    }

    @Override
    public void close() {
        release(apmSpan.getSpan());
        if (finishSpanOnClose) {
            apmSpan.finish();
        }
    }

    private void release(Object span) {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ApmScopeInstrumentation
    }

    @Override
    public ApmSpan span() {
        return apmSpan;
    }

    @Override
    public String toString() {
        return String.format("Scope(%s)", apmSpan);
    }
}
