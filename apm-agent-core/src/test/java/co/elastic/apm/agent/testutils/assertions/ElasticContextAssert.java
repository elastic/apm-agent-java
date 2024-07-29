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
package co.elastic.apm.agent.testutils.assertions;

import co.elastic.apm.agent.impl.transaction.TraceStateImpl;

public class ElasticContextAssert<SELF extends ElasticContextAssert<SELF, ACTUAL>, ACTUAL extends TraceStateImpl<?>> extends BaseAssert<SELF, ACTUAL> {

    protected ElasticContextAssert(ACTUAL actual, Class<SELF> selfType) {
        super(actual, selfType);
    }

    public SELF hasBaggageCount(int expectedCount) {
        isNotNull();
        new BaggageAssert(actual.getBaggage()).hasSize(expectedCount);
        return thiz();
    }

    public SELF hasBaggage(String key, String value) {
        isNotNull();
        new BaggageAssert(actual.getBaggage()).containsEntry(key, value);
        return thiz();
    }

    @SuppressWarnings("unchecked")
    private SELF thiz() {
        return (SELF) this;
    }
}
