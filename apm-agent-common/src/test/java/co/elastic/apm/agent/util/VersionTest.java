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
        "1.0.0,                          <, 1.0.1",
        "1.2.3,                          =, 1.2.3",
        "1.2.3-SNAPSHOT,                 <, 1.2.3",
        "4.5.13,                         =, 4.5.13",
        "4.5.13.redhat-00001,            <, 4.5.13",
        "httpclient-4.5.13,              =, 4.5.13",
        "httpclient-4.5.13.redhat-00001, <, 4.5.13",
        "httpclient.4.5.13.redhat-00001, =, 4.5.13.redhat-00001",
        "httpclient.4.5.13.redhat,       <, 4.5.13",
        "httpclient.4.5.13-redhat,       =, 4.5.13-redhat",
        "1,                              =, 1",
        "1,                              <, 1.2",
        "1.2,                            =, 1.2",
        "1.2.3,                          =, 1.2.3",
        "1.2.3.4,                        =, 1.2.3.4",
        "ignore.1.2.3-pre1,              =, 1.2.3-pre1",
        "1.2.3.rc1,                      <, 1.2.3.rc2"
    })
    void testVersion(String version1, String operator, String version2) {
        switch (operator) {
            case "<":
                assertThat(Version.of(version1)).isLessThan(Version.of(version2));
                assertThat(Version.of(version2)).isGreaterThan(Version.of(version1));
                break;
            case "=":
                assertThat(Version.of(version1)).isEqualByComparingTo(Version.of(version2));
                assertThat(Version.of(version1)).isEqualTo(Version.of(version2));
                assertThat(Version.of(version1).toString()).isEqualTo(Version.of(version2).toString());
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
