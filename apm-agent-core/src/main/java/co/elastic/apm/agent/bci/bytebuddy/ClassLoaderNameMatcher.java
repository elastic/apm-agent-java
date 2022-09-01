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

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.NameMatcher;
import net.bytebuddy.matcher.StringMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class ClassLoaderNameMatcher {

    public static ElementMatcher.Junction<ClassLoader> classLoaderWithName(final String name) {
        return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
            @Override
            public boolean matches(ClassLoader target) {
                return target != null && target.getClass().getName().equals(name);
            }
        };
    }

    public static ElementMatcher.Junction<ClassLoader> classLoaderWithNamePrefix(final String name) {
        return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
            @Override
            public boolean matches(ClassLoader target) {
                return target != null && target.getClass().getName().startsWith(name);
            }
        };
    }



    public static ElementMatcher.Junction<ClassLoader> isReflectionClassLoader() {
        return classLoaderWithName("sun.reflect.DelegatingClassLoader")
            .or(classLoaderWithName("jdk.internal.reflect.DelegatingClassLoader"));
    }

}
