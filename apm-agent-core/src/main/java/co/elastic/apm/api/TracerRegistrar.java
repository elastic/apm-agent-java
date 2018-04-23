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
package co.elastic.apm.api;

import co.elastic.apm.impl.ElasticApmTracer;

/**
 * This class is only intended to be used by {@link ElasticApmTracer} to register itself to the public API {@link ElasticApm}
 */
public class TracerRegistrar {
    public static void register(ElasticApmTracer tracer) {
        ElasticApm.get().register(tracer);
    }

    public static void unregister() {
        ElasticApm.get().unregister();
    }
}
