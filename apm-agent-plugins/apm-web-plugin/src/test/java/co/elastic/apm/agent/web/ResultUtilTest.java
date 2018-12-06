/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ResultUtilTest {

    @Test
    void getResult() {
        assertSoftly(softly -> {
            softly.assertThat(ResultUtil.getResultByHttpStatus(100)).isEqualTo("HTTP 1xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(199)).isEqualTo("HTTP 1xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(200)).isEqualTo("HTTP 2xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(299)).isEqualTo("HTTP 2xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(300)).isEqualTo("HTTP 3xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(399)).isEqualTo("HTTP 3xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(400)).isEqualTo("HTTP 4xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(499)).isEqualTo("HTTP 4xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(500)).isEqualTo("HTTP 5xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(599)).isEqualTo("HTTP 5xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(600)).isNull();
            softly.assertThat(ResultUtil.getResultByHttpStatus(20)).isNull();
            softly.assertThat(ResultUtil.getResultByHttpStatus(0)).isNull();
            softly.assertThat(ResultUtil.getResultByHttpStatus(-1)).isNull();
        });
    }

}
