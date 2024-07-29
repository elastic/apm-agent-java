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

import co.elastic.apm.agent.impl.baggage.BaggageImpl;

import javax.annotation.Nullable;
import java.util.Objects;

public class BaggageAssert extends BaseAssert<BaggageAssert, BaggageImpl> {

    protected BaggageAssert(BaggageImpl actual) {
        super(actual, BaggageAssert.class);
    }

    public BaggageAssert hasSize(int expectedSize) {
        isNotNull();
        checkInt("Expected baggage to have size %d but %d", expectedSize, actual.keys().size());
        return this;
    }

    public BaggageAssert containsEntry(String key, String value) {
        isNotNull();
        if (!actual.keys().contains(key)) {
            failWithMessage("Expected baggage to contain key '%s' but did not. Contained keys are %s", key, actual.keys());
        }
        if (!value.equals(actual.get(key))) {
            failWithMessage("Expected baggage to contain value '%s' for key '%s' but actual was '%s'", value, key, actual.get(key));
        }
        return this;
    }

    public BaggageAssert containsEntry(String key, String value, @Nullable String metadata) {
        isNotNull();
        containsEntry(key, value);
        if (!Objects.equals(metadata, actual.getMetadata(key))) {
            failWithMessage("Expected baggage to contain metadata '%s' for key '%s' but actual was '%s'", metadata, key, actual.getMetadata(key));
        }
        return this;
    }
}
