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
package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.Test;

import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class OTelHelperTest {

    @Test
    public void testMappingOfAllValues() {
        Arrays.asList(SpanKind.values())
            .forEach(otelKind -> {
                OTelSpanKind oTelSpanKind = null;
                try {
                    oTelSpanKind = OTelHelper.map(otelKind);
                    assertThat(oTelSpanKind).isNotNull();
                    assertThat(oTelSpanKind.name()).isEqualTo(otelKind.toString());
                } catch (NoSuchElementException e) {
                    fail(String.format("Exception should not be thrown with otelKind %s. Please check your OTelSpanKind class with new values.", otelKind));
                }
            });
    }

    @Test
    public void testMappingOfNullValue() {
        OTelSpanKind oTelSpanKind = OTelHelper.map(null);

        assertThat(oTelSpanKind).isNotNull().isEqualTo(OTelSpanKind.INTERNAL);
    }
}
