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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

class CharSequenceUtilsTest {

    @Test
    void testEqualsHashCode() {

        assertThat(CharSequenceUtils.equals(null, null)).isTrue();

        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        checkEqual(sb1, sb2);
        checkEqual(sb1, sb1); // should be equal to itself when empty

        sb1.append("a");
        checkNotEqual(sb1, sb2);
        checkEqual(sb1, sb1); // should be equal to itself when not empty

        sb2.append("a");
        checkEqual(sb1, sb2);
    }

    private void checkEqual(CharSequence cs1, CharSequence cs2){
        assertThat(CharSequenceUtils.equals(cs1, cs2)).isTrue();
        assertThat(CharSequenceUtils.hashCode(cs1)).isEqualTo(CharSequenceUtils.hashCode(cs2));
    }

    private void checkNotEqual(CharSequence cs1, CharSequence cs2){
        assertThat(CharSequenceUtils.equals(cs1, cs2)).isFalse();
        assertThat(CharSequenceUtils.hashCode(cs1)).isNotEqualTo(CharSequenceUtils.hashCode(cs2));
    }

}
