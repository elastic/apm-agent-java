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
package co.elastic.apm.agent.sdk.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * This implementation is based on org.stagemonitor.core.instrument.OverridesMethodElementMatcher,
 * under Apache License 2.0
 *
 * @see CustomElementMatchers#overridesOrImplementsMethodThat(ElementMatcher)
 */
public class MethodHierarchyMatcher extends ElementMatcher.Junction.AbstractBase<MethodDescription> {

    private final ElementMatcher<? super MethodDescription> extraMethodMatcher;
    private final ElementMatcher<? super TypeDescription> superClassMatcher;
    private final ElementMatcher<? super TypeDescription> hierarchyMatcher;

    MethodHierarchyMatcher(ElementMatcher<? super MethodDescription> extraMethodMatcher) {
        this(extraMethodMatcher, not(is(TypeDescription.ForLoadedType.of(Object.class))), any());
    }

    private MethodHierarchyMatcher(ElementMatcher<? super MethodDescription> extraMethodMatcher, ElementMatcher<? super TypeDescription> superClassMatcher, ElementMatcher<? super TypeDescription> hierachyMatcher) {
        this.extraMethodMatcher = extraMethodMatcher;
        this.superClassMatcher = superClassMatcher;
        this.hierarchyMatcher = hierachyMatcher;
    }

    public MethodHierarchyMatcher onSuperClassesThat(ElementMatcher<? super TypeDescription> superClassMatcher) {
        return new MethodHierarchyMatcher(extraMethodMatcher, superClassMatcher, hierarchyMatcher);
    }

    public MethodHierarchyMatcher whereHierarchyContains(ElementMatcher<? super TypeDescription> hierarchyMatcher) {
        return new MethodHierarchyMatcher(extraMethodMatcher, superClassMatcher, hierarchyMatcher);
    }

    @Override
    public boolean matches(MethodDescription targetMethod) {
        return declaresInHierarchy(targetMethod, targetMethod.getDeclaringType().asErasure(), new ArrayDeque<TypeDescription>());
    }

    private boolean declaresInHierarchy(MethodDescription targetMethod, TypeDescription type, Deque<TypeDescription> hierarchy) {
        hierarchy.push(type);
        try {
            if (declaresMethod(named(targetMethod.getName())
                .and(returns(targetMethod.getReturnType().asErasure()))
                .and(takesArguments(targetMethod.getParameters().asTypeList().asErasures()))
                .and(extraMethodMatcher))
                .matches(type)
             && !new TypeList.Explicit(new ArrayList<>(hierarchy))
                .filter(hierarchyMatcher)
                .isEmpty()
            ) {
                return true;
            }
            for (TypeDescription interfaze : type.getInterfaces().asErasures()) {
                if (superClassMatcher.matches(interfaze)) {
                    if (declaresInHierarchy(targetMethod, interfaze, hierarchy)) {
                        return true;
                    }
                }
            }
            final TypeDescription.Generic superClass = type.getSuperClass();
            if (superClass != null && superClassMatcher.matches(superClass.asErasure())) {
                return declaresInHierarchy(targetMethod, superClass.asErasure(), hierarchy);
            }
            return false;
        } finally {
            hierarchy.pop();
        }
    }

}
