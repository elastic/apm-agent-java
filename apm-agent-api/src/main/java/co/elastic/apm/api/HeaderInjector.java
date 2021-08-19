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

/**
 * Used to inject a header into a networking library's underlying outgoing request object.
 * <p>
 * Can be implemented as a lambda in Java 8 and as an anonymous inner class in Java 7.
 * </p>
 */
public interface HeaderInjector {

    /**
     * Injects a header key and value into the underlying request object of an networking library
     *
     * @param headerName  the name of the header
     * @param headerValue the value of the header
     */
    void addHeader(String headerName, String headerValue);
}
