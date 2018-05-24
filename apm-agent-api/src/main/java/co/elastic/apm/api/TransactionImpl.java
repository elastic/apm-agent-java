/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.api;

class TransactionImpl implements Transaction {

    @SuppressWarnings("unused")
    private final Object transaction;

    public TransactionImpl(Object transaction) {
        this.transaction = transaction;
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public void setType(String type) {

    }

    @Override
    public void addTag(String key, String value) {

    }

    @Override
    public void setUser(String id, String email, String username) {

    }

    @Override
    public void end() {

    }

    @Override
    public void close() {

    }
}
