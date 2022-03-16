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
package co.elastic.apm.agent.logback;

public class LegacyLogbackInstrumentationTest extends LogbackInstrumentationTest {

    @Override
    public void testReformattedLogRolling() {
        // Logback's SizeBasedTriggeringPolicy relies on something called InvocationGate to limit the frequency of
        // heavy operations like file rolling. In versions older than 1.1.8, its implementation made it unsuitable
        // for unit testing as it required minimal number of invocations (starts with 16 an increasing) that cannot be
        // done too frequently (less than 100 ms apart).
        // The way to hack it is get the InvocationGate through reflection and assign it with:
        // invocationCounter = mask - 1, but its a bit of an overkill, so we just skip this one...
    }
}
