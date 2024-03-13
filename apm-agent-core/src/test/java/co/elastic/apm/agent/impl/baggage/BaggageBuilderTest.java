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
package co.elastic.apm.agent.impl.baggage;


import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

public class BaggageBuilderTest {

    @Test
    public void verifyBaggageReuseOnNoModification() {
        BaggageImpl base = BaggageImpl.builder()
            .put("foo", "bar")
            .build();

        BaggageImpl newlyBuilt = base.toBuilder().build();

        assertThat(newlyBuilt).isSameAs(base);
    }

    @Test
    public void verifyBaggageBuilderPreserversOriginalBaggage() {
        BaggageImpl base = BaggageImpl.builder()
            .put("foo", "bar")
            .put("bar", "baz")
            .build();

        BaggageImpl newlyBuilt = base.toBuilder()
            .put("foo", "not-bar")
            .build();

        assertThat(base)
            .containsEntry("foo", "bar")
            .containsEntry("bar", "baz");
        assertThat(newlyBuilt)
            .containsEntry("foo", "not-bar")
            .containsEntry("bar", "baz");
    }

    @Test
    public void testEntryRemoval() {
        BaggageImpl base = BaggageImpl.builder()
            .put("foo", "bar")
            .put("bar", "baz")
            .build();

        BaggageImpl newlyBuilt = base.toBuilder()
            .put("foo", null)
            .build();

        assertThat(base)
            .hasSize(2)
            .containsEntry("foo", "bar")
            .containsEntry("bar", "baz");
        assertThat(newlyBuilt)
            .hasSize(1)
            .containsEntry("bar", "baz");
    }

    @Test
    public void verifyMetadataPreserved() {
        BaggageImpl base = BaggageImpl.builder()
            .put("foo", "bar", "meta1")
            .put("bar", "baz", "meta2")
            .build();

        BaggageImpl newlyBuilt = base.toBuilder()
            .put("foo", "not-bar")
            .put("new", "newval", "new_meta")
            .build();

        assertThat(base)
            .containsEntry("foo", "bar", "meta1")
            .containsEntry("bar", "baz", "meta2");
        assertThat(newlyBuilt)
            .containsEntry("foo", "not-bar", null)
            .containsEntry("bar", "baz", "meta2")
            .containsEntry("new", "newval", "new_meta");
    }
}
