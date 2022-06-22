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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTest {

    @ParameterizedTest
    @CsvSource(value = {
        "1.0.0,                          <, 1.0.1,               false",
        "1.2.3,                          =, 1.2.3,               false",
        "1.2.3-pre1,                     <, 1.2.3,               false",
        "1.2.3.rc1,                      <, 1.2.3.rc2,           false",
        "1.2.3-SNAPSHOT,                 <, 1.2.3.RC1,           false",
        "1.2.3-SNAPSHOT,                 <, 1.2.3,               false",
        "4.5.13,                         =, 4.5.13,              false",
        "4.5.13.redhat-00001,            <, 4.5.13,              false",
        "httpclient-4.5.13,              =, 4.5.13,              false",
        "httpclient-4.5.13.redhat-00001, =, 4.5.13,              true",
        "httpclient.4.5.13.redhat-00001, =, 4.5.13.redhat-00001, true",
        "httpclient.4.5.13.redhat,       =, 4.5.13,              true",
        "httpclient.4.5.13-redhat,       =, 4.5.13-redhat,       true",
        "1,                              =, 1,                   false",
        "1,                              <, 1.2,                 false",
        "1.2,                            =, 1.2,                 false",
        "1.2.3,                          =, 1.2.3,               false",
        "1.2.3.4,                        =, 1.2.3.4,             false",
        "ignore.1.2.3-pre1,              =, 1.2.3-pre1,          false",
    })
    void testVersion(String version1, String operator, String version2, boolean ignoreSuffix) {
        Version v1 = Version.of(version1);
        Version v2 = Version.of(version2);
        if (ignoreSuffix) {
            v1 = v1.withoutSuffix();
            v2 = v2.withoutSuffix();
        }
        switch (operator) {
            case "<":
                assertThat(v1).isLessThan(v2);
                assertThat(v2).isGreaterThan(v1);
                break;
            case "=":
                assertThat(v1).isEqualByComparingTo(v2);
                assertThat(v1).isEqualTo(v2);
                assertThat(v1.toString()).isEqualTo(v2.toString());
                break;
            default:
                throw new IllegalArgumentException("Invalid operator: " + operator);
        }
    }

    @Test
    void testEmptyVersion() {
        assertThat(Version.of("").toString()).isEqualTo("");
    }
}
