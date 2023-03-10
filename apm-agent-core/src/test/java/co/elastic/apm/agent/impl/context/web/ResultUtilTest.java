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
package co.elastic.apm.agent.impl.context.web;

import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static co.elastic.apm.agent.impl.context.web.ResultUtil.getOutcomeByHttpClientStatus;
import static co.elastic.apm.agent.impl.context.web.ResultUtil.getOutcomeByHttpServerStatus;
import static co.elastic.apm.agent.impl.context.web.ResultUtil.getResultByHttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

class ResultUtilTest {

    @ParameterizedTest
    @CsvSource({
        "-1,FAILURE,FAILURE,",
        "0,FAILURE,FAILURE,",
        "1,FAILURE,FAILURE,",
        "99,FAILURE,FAILURE,",
        "100,SUCCESS,SUCCESS,HTTP 1xx",
        "199,SUCCESS,SUCCESS,HTTP 1xx",
        "200,SUCCESS,SUCCESS,HTTP 2xx",
        "299,SUCCESS,SUCCESS,HTTP 2xx",
        "300,SUCCESS,SUCCESS,HTTP 3xx",
        "399,SUCCESS,SUCCESS,HTTP 3xx",
        "400,FAILURE,SUCCESS,HTTP 4xx",
        "499,FAILURE,SUCCESS,HTTP 4xx",
        "500,FAILURE,FAILURE,HTTP 5xx",
        "599,FAILURE,FAILURE,HTTP 5xx",
        "600,FAILURE,FAILURE,"
    })
    void testHttpStatus(int status, Outcome clientOutcome, Outcome serverOutcome, String expectedResult) {
        assertThat(getOutcomeByHttpClientStatus(status)).isEqualTo(clientOutcome);
        assertThat(getOutcomeByHttpServerStatus(status)).isEqualTo(serverOutcome);
        assertThat(getResultByHttpStatus(status)).isEqualTo(expectedResult);
    }

}
