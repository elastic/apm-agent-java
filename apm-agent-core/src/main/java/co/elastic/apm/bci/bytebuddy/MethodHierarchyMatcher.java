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
package co.elastic.apm.bci.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

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

    MethodHierarchyMatcher(ElementMatcher<? super MethodDescription> extraMethodMatcher) {
        this(extraMethodMatcher, not(is(TypeDescription.ForLoadedType.OBJECT)));
    }

    private MethodHierarchyMatcher(ElementMatcher<? super MethodDescription> extraMethodMatcher, ElementMatcher<? super TypeDescription> superClassMatcher) {
        this.extraMethodMatcher = extraMethodMatcher;
        this.superClassMatcher = superClassMatcher;
    }

    public ElementMatcher<MethodDescription> onSuperClassesThat(ElementMatcher<? super TypeDescription> superClassMatcher) {
        return new MethodHierarchyMatcher(extraMethodMatcher, superClassMatcher);
    }

    @Override
    public boolean matches(MethodDescription targetMethod) {
        return declaresInHierarchy(targetMethod, targetMethod.getDeclaringType().asErasure());
    }

    private boolean declaresInHierarchy(MethodDescription targetMethod, TypeDescription type) {
        if (declaresMethod(named(targetMethod.getName())
            .and(returns(targetMethod.getReturnType().asErasure()))
            .and(takesArguments(targetMethod.getParameters().asTypeList().asErasures()))
            .and(extraMethodMatcher))
            .matches(type)) {
            return true;
        }
        for (TypeDescription interfaze : type.getInterfaces().asErasures()) {
            if (superClassMatcher.matches(interfaze)) {
                if (declaresInHierarchy(targetMethod, interfaze)) {
                    return true;
                }
            }
        }
        final TypeDescription.Generic superClass = type.getSuperClass();
        if (superClass != null && superClassMatcher.matches(superClass.asErasure())) {
            return declaresInHierarchy(targetMethod, superClass.asErasure());
        }
        return false;
    }

}
