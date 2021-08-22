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
package co.elastic.apm.api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class ApiMethodHandles {
    /**
     * MethodHandle for {@link HeaderExtractor#getFirstHeader(String)}
     */
    static final MethodHandle GET_FIRST_HEADER;
    /**
     * MethodHandle for {@link HeadersExtractor#getAllHeaders(String)}
     */
    static final MethodHandle GET_ALL_HEADERS;
    /**
     * MethodHandle for {@link HeaderInjector#addHeader(String, String)}
     */
    static final MethodHandle ADD_HEADER;

    static {
        try {
            GET_FIRST_HEADER = MethodHandles.lookup()
                .findVirtual(HeaderExtractor.class, "getFirstHeader", MethodType.methodType(String.class, String.class));
            GET_ALL_HEADERS = MethodHandles.lookup()
                .findVirtual(HeadersExtractor.class, "getAllHeaders", MethodType.methodType(Iterable.class, String.class));
            ADD_HEADER = MethodHandles.lookup()
                .findVirtual(HeaderInjector.class, "addHeader", MethodType.methodType(void.class, String.class, String.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
