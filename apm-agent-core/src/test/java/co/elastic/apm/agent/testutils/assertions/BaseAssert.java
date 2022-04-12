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

import org.assertj.core.api.AbstractAssert;

import javax.annotation.Nullable;

public class BaseAssert<SELF extends AbstractAssert<SELF, ACTUAL>, ACTUAL> extends AbstractAssert<SELF, ACTUAL> {

    protected BaseAssert(ACTUAL actual, Class<SELF> selfType) {
        super(actual, selfType);
    }

    protected static String normalizeToString(CharSequence cs) {
        return cs == null ? null : cs.toString();
    }

    protected void checkString(String msg, String expected, @Nullable String actual) {
        if (!expected.equals(actual)) {
            failWithMessage(msg, expected, actual);
        }
    }

    protected void checkInt(String msg, int expected, int actual){
        if (expected != actual) {
            failWithMessage(msg, expected, actual);
        }
    }

    protected void checkNull(String msg, @Nullable Object actual) {
        if (actual != null) {
            failWithMessage(msg, actual);
        }
    }

    protected void checkTrue(String msg, boolean expectedTrue) {
        if (!expectedTrue) {
            failWithMessage(msg);
        }
    }
}
