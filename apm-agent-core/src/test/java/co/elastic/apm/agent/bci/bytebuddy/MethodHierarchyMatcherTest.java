/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.bci.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static org.assertj.core.api.Assertions.assertThat;

class MethodHierarchyMatcherTest {

    @Test
    void testMatchInSameClass() throws Exception {
        assertThat(CustomElementMatchers.overridesOrImplementsMethodThat(isAnnotatedWith(FindMe.class))
            .matches(new MethodDescription.ForLoadedMethod(TestClass.class.getDeclaredMethod("findInSameClass"))))
            .isTrue();
    }

    @Test
    void testMatchInSuperClass() throws Exception {
        assertThat(CustomElementMatchers
            .overridesOrImplementsMethodThat(isAnnotatedWith(FindMe.class))
            .onSuperClassesThat(nameContains("Super"))
            .matches(new MethodDescription.ForLoadedMethod(TestClass.class.getDeclaredMethod("findInSuperClass"))))
            .isTrue();
    }

    @Test
    void testMatchInSuperClass_NotMatchedBySuperClassMatcher() throws Exception {
        assertThat(CustomElementMatchers
            .overridesOrImplementsMethodThat(isAnnotatedWith(FindMe.class))
            .onSuperClassesThat(not(nameContains("Super")))
            .matches(new MethodDescription.ForLoadedMethod(TestClass.class.getDeclaredMethod("findInSuperClass"))))
            .isFalse();
    }

    @Test
    void testMatchInInterfaceOfSuperClass() throws Exception {
        assertThat(CustomElementMatchers.overridesOrImplementsMethodThat(isAnnotatedWith(FindMe.class))
            .matches(new MethodDescription.ForLoadedMethod(TestClass.class.getDeclaredMethod("findInInterfaceOfSuperClass"))))
            .isTrue();
    }

    @Test
    void testMatchInSuperInterface() throws Exception {
        assertThat(CustomElementMatchers.overridesOrImplementsMethodThat(isAnnotatedWith(FindMe.class))
            .matches(new MethodDescription.ForLoadedMethod(TestClass.class.getDeclaredMethod("findInSuperInterface"))))
            .isTrue();
    }

    @Test
    void testMatchInInterface() throws Exception {
        assertThat(CustomElementMatchers.overridesOrImplementsMethodThat(isAnnotatedWith(FindMe.class))
            .matches(new MethodDescription.ForLoadedMethod(TestClass.class.getDeclaredMethod("findInInterface"))))
            .isTrue();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FindMe {
    }

    public interface SuperInterface {
        @FindMe
        void findInSuperInterface();
    }

    public interface Interfaze extends SuperInterface {
        @FindMe
        void findInInterface();
    }

    public interface InterfaceOfSuperClass {
        @FindMe
        void findInInterfaceOfSuperClass();
    }

    public abstract static class SuperClass implements InterfaceOfSuperClass {
        @FindMe
        public void findInSuperClass() {
        }
    }

    public static class TestClass extends SuperClass implements Interfaze {
        @FindMe
        public void findInSameClass() {
        }

        @Override
        public void findInSuperClass() {
        }

        @Override
        public void findInInterfaceOfSuperClass() {
        }

        @Override
        public void findInSuperInterface() {
        }

        @Override
        public void findInInterface() {

        }
    }
}
