/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TextTracestateAppenderTest {

    private TextTracestateAppender tracestateAppender;
    private StringBuilder stringBuilder;

    @BeforeEach
    void setUp() {
        tracestateAppender = new TextTracestateAppender();
        stringBuilder = new StringBuilder();
    }

    @Test
    void testSimpleAppend() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("three=four", stringBuilder, 20);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four");
    }

    @Test
    void testOverflow() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("three=four", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("five=six", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("seven=eight", stringBuilder, 20);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four");
    }

    @Test
    void testOverflowWithinFirstHeader() {
        tracestateAppender.appendTracestateHeaderValue("one=two,three=four,five=six,seven=eight", stringBuilder, 20);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four");
    }

    @Test
    void testOverflowWithinHeader() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("three=four,five=six", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("seven=eight,nine=ten", stringBuilder, 20);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four");
    }

    @Test
    void testIncludeEmptyEntry() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("three=four, ,five=six", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("seven=eight,nine=ten", stringBuilder, 20);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four, ");
    }

    @Test
    void testMultipleOverflowValues() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("three=four,five=six,seven=eight", stringBuilder, 20);
        tracestateAppender.appendTracestateHeaderValue("nine=ten", stringBuilder, 20);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four");
    }

    @Test
    void testExactCutoffOnSeparator() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 18);
        tracestateAppender.appendTracestateHeaderValue("three=four,five=six,seven=eight", stringBuilder, 18);
        tracestateAppender.appendTracestateHeaderValue("nine=ten", stringBuilder, 18);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,three=four");
    }

    @Test
    void testPickFittingValue() {
        tracestateAppender.appendTracestateHeaderValue("one=two", stringBuilder, 17);
        tracestateAppender.appendTracestateHeaderValue("three=four,five=six,seven=eight", stringBuilder, 17);
        tracestateAppender.appendTracestateHeaderValue("nine=ten,eleven-twelve", stringBuilder, 17);
        assertThat(stringBuilder.toString()).isEqualTo("one=two,nine=ten");
    }
}
