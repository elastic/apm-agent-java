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

import io.opentracing.propagation.TextMap;

import javax.annotation.Nullable;
import java.util.Map;

class ExternalProcessSpanContext implements ApmSpanContext {
    private final TextMap textMap;

    private ExternalProcessSpanContext(TextMap textMap) {
        this.textMap = textMap;
    }

    static ExternalProcessSpanContext of(TextMap textMap) {
        return new ExternalProcessSpanContext(textMap);
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return textMap;
    }

    @Nullable
    @Override
    public Object getTraceContext() {
        return null;
    }
}
