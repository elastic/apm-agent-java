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
package co.elastic.apm.agent.sdk;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.ServiceLoader;

public class DynamicTransformer {
    private static final DynamicTransformerSupplier transformer;

    static {
        ClassLoader classLoader = DynamicTransformer.class.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        // loads the implementation provided by the core module without depending on the class or class name
        transformer = ServiceLoader.load(DynamicTransformerSupplier.class, classLoader).iterator().next();
    }

    /**
     * Instruments a specific class at runtime with one or multiple instrumentation classes.
     * <p>
     * Note that {@link ElasticApmInstrumentation#getTypeMatcher()} will be
     * {@linkplain net.bytebuddy.matcher.ElementMatcher.Junction#and(ElementMatcher) conjoined} with a
     * computed {@link ElementMatcher}{@code <}{@link TypeDescription}{@code >}
     * that is specific to the provided class to instrument.
     * </p>
     *
     * @param classToInstrument      the class which should be instrumented.
     * @param instrumentationClasses the instrumentation which should be applied to the class to instrument.
     */
    public static void ensureInstrumented(Class<?> classToInstrument, Collection<Class<? extends ElasticApmInstrumentation>> instrumentationClasses) {
        transformer.ensureInstrumented(classToInstrument, instrumentationClasses);
    }

    public interface DynamicTransformerSupplier {

        void ensureInstrumented(Class<?> classToInstrument, Collection<Class<? extends ElasticApmInstrumentation>> instrumentationClasses);

    }
}
