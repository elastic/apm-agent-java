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
package co.elastic.apm.agent.bci.bytebuddy;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.matcher.AnnotationMatcher;
import co.elastic.apm.agent.util.ClassLoaderUtils;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.List;

public class CustomElementMatchers {

    private static final ElementMatcher.Junction.AbstractBase<ClassLoader> AGENT_CLASS_LOADER_MATCHER = new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
        @Override
        public boolean matches(@Nullable ClassLoader classLoader) {
            return ClassLoaderUtils.isAgentClassLoader(classLoader);
        }
    };

    private static final ElementMatcher.Junction.AbstractBase<ClassLoader> INTERNAL_PLUGIN_CLASS_LOADER_MATCHER = new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
        @Override
        public boolean matches(@Nullable ClassLoader classLoader) {

            boolean result = ClassLoaderUtils.isInternalPluginClassLoader(classLoader);
            return result;
        }
    };

    /**
     * Matches the target class loader to a given class loader by instance comparison
     *
     * @param other the class loader to match to
     * @return {@code true} if {@code other} is the same class loader instance as the target class loader
     */
    public static ElementMatcher.Junction<ClassLoader> isSameClassLoader(final ClassLoader other) {
        return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
            @Override
            public boolean matches(@Nullable ClassLoader target) {
                return target == other;
            }
        };
    }

    public static ElementMatcher.Junction<ClassLoader> isAgentClassLoader() {
        return AGENT_CLASS_LOADER_MATCHER;
    }

    public static ElementMatcher.Junction<ClassLoader> isInternalPluginClassLoader() {
        return INTERNAL_PLUGIN_CLASS_LOADER_MATCHER;
    }

    public static ElementMatcher.Junction<NamedElement> matches(final WildcardMatcher matcher) {
        return new ElementMatcher.Junction.AbstractBase<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                return matcher.matches(target.getActualName());
            }

            @Override
            public String toString() {
                return "matches(" + matcher + ")";
            }
        };
    }

    public static ElementMatcher.Junction<NamedElement> anyMatch(final List<WildcardMatcher> matchers) {
        return new ElementMatcher.Junction.AbstractBase<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                return WildcardMatcher.isAnyMatch(matchers, target.getActualName());
            }

            @Override
            public String toString() {
                return "matches(" + matchers + ")";
            }
        };
    }

    public static ElementMatcher.Junction<AnnotationSource> annotationMatches(final String annotationWildcard) {
        return AnnotationMatcher.annotationMatcher(annotationWildcard);
    }
}
