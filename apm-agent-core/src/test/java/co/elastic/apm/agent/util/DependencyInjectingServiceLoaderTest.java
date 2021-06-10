/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyInjectingServiceLoaderTest {

    @Test
    void testServiceWithConstructorArgument() {
        final List<Service> serviceImplementations = DependencyInjectingServiceLoader.load(Service.class, "foo");
        assertThat(serviceImplementations).hasSize(2);
        assertThat(serviceImplementations.get(0).getString()).isEqualTo("foo");
        assertThat(serviceImplementations.get(0)).isInstanceOf(ServiceImpl.class);
        assertThat(serviceImplementations.get(1).getString()).isEqualTo("foo");
        assertThat(serviceImplementations.get(1)).isInstanceOf(ServiceImpl2.class);
    }

    @Test
    void testDuplicatedServiceImplementations() {
        List<ClassLoader> classLoaders = new ArrayList<>();
        classLoaders.add(getClass().getClassLoader());
        classLoaders.add(new URLClassLoader(new URL[0], getClass().getClassLoader()));
        final List<Service> serviceImplementations = DependencyInjectingServiceLoader.load(Service.class, classLoaders,"foo");
        assertThat(serviceImplementations).hasSize(2);
        assertThat(serviceImplementations.get(0).getString()).isEqualTo("foo");
        assertThat(serviceImplementations.get(0)).isInstanceOf(ServiceImpl.class);
        assertThat(serviceImplementations.get(1).getString()).isEqualTo("foo");
        assertThat(serviceImplementations.get(1)).isInstanceOf(ServiceImpl2.class);
    }

    @Test
    void testServiceWithoutConstructorArgument() {
        final List<Service> serviceImplementations = DependencyInjectingServiceLoader.load(Service.class);
        assertThat(serviceImplementations).hasSize(2);
        assertThat(serviceImplementations.get(0).getString()).isEqualTo("bar");
        assertThat(serviceImplementations.get(0)).isInstanceOf(ServiceImpl.class);
        assertThat(serviceImplementations.get(1).getString()).isEqualTo("bar");
        assertThat(serviceImplementations.get(1)).isInstanceOf(ServiceImpl2.class);
    }

    public interface Service {
        String getString();
    }

    public static class ServiceImpl implements Service {

        private final String string;

        public ServiceImpl() {
            this("bar");
        }

        public ServiceImpl(String s) {
            string = s;
        }

        @Override
        public String getString() {
            return string;
        }
    }

    public static class ServiceImpl2 implements Service {

        private final String string;

        public ServiceImpl2() {
            this("bar");
        }

        public ServiceImpl2(Object o) {
            string = o.toString();
        }

        @Override
        public String getString() {
            return string;
        }
    }


}
