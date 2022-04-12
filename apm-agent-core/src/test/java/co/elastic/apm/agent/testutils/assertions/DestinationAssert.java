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

import co.elastic.apm.agent.impl.context.Destination;

public class DestinationAssert extends BaseAssert<DestinationAssert, Destination> {

    public DestinationAssert(Destination actual) {
        super(actual, DestinationAssert.class);
    }

    public DestinationAssert hasAddress(String address) {
        isNotNull();
        checkString("Expected destination with address '%s' but was '%s'", address, normalizeToString(actual.getAddress()));
        return this;
    }

    public DestinationAssert hasEmptyAddress() {
        isNotNull();
        checkString("Expected destination with empty address '%s' but was '%s'", "", normalizeToString(actual.getAddress()));
        return this;
    }

    public DestinationAssert hasPort(int port) {
        isNotNull();
        checkInt("Expected destination with port '%d' but was '%d'", port, actual.getPort());
        return this;
    }

    public DestinationAssert hasNoPort() {
        isNotNull();
        checkTrue("Expected destination without port", actual.getPort() < 0);
        return this;
    }

    public DestinationAssert isEmpty() {
        isNotNull();
        checkTrue("Expected empty destination", !actual.hasContent());
        return this;
    }
}
