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

import co.elastic.apm.agent.impl.transaction.Span;

public class SpanAssert extends BaseAssert<SpanAssert, Span> {

    protected SpanAssert(Span actual) {
        super(actual, SpanAssert.class);
    }

    public SpanAssert hasName(String name) {
        isNotNull();
        checkString("Expected span with name '%s' but was '%s'", name, normalizeToString(actual.getNameForSerialization()));
        return this;
    }

    public SpanAssert hasType(String type) {
        isNotNull();
        checkString("Expected span with type '%s' but was '%s'", type, actual.getType());
        return this;
    }

    public SpanAssert hasSubType(String subType) {
        isNotNull();
        checkString("Expected span with subtype '%s' but was '%s'", subType, actual.getSubtype());
        return this;
    }

    public SpanAssert hasAction(String action) {
        isNotNull();
        checkString("Expected span with subtype '%s' but was '%s'", action, actual.getAction());
        return this;
    }

    public SpanAssert hasDbStatement(String statement) {
        isNotNull();
        checkString("Expected span with DB statement '%s' but was '%s'", statement, actual.getContext().getDb().getStatement());
        return this;
    }

    public SpanAssert hasDbInstance(String instance) {
        isNotNull();
        checkString("Expected span with DB instance '%s' but was '%s'", instance, actual.getContext().getDb().getInstance());
        return this;
    }
}
