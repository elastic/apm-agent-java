/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

import io.opentracing.SpanContext;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

public interface ApmSpanContext extends SpanContext {

    @Nullable
    String getTraceParentHeader();

    class ForHeader implements ApmSpanContext {
        private final String traceParentHeader;

        private ForHeader(String traceParentHeader) {
            this.traceParentHeader = traceParentHeader;
        }

        public static ForHeader of(String traceParentHeader) {
            return new ForHeader(traceParentHeader);
        }

        @Override
        public String getTraceParentHeader() {
            return traceParentHeader;
        }

        @Override
        public Iterable<Map.Entry<String, String>> baggageItems() {
            return Collections.emptyList();
        }
    }
}
