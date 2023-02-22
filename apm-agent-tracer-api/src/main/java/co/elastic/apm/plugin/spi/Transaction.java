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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Transaction<T extends Transaction<T>> extends AbstractSpan<T> {

    String TYPE_REQUEST = "request";

    TransactionContext getContext();

    boolean isNoop();

    void ignoreTransaction();

    void addCustomContext(String key, String value);

    void addCustomContext(String key, Number value);

    void addCustomContext(String key, Boolean value);

    void setFrameworkName(@Nullable String frameworkName);

    void setUserFrameworkName(@Nullable String frameworkName);

    T captureException(@Nullable Throwable thrown);

    T withResult(@Nullable String result);

    T withResultIfUnset(@Nullable String result);

    String getType();

    void setFrameworkVersion(@Nullable String frameworkVersion);

    Faas getFaas();

    void setUser(String id, String email, String username, String domain);
}
