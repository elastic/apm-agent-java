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
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.List;

public class CustomElementMatchers {

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
