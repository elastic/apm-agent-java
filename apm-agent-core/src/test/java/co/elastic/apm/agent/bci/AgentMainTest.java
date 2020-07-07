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
package co.elastic.apm.agent.bci;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMainTest {

    private static String HOTSPOT_VM_NAME = "Java HotSpot(TM) 64-Bit Server VM";

    @Test
    void java6AndEarlierNotSupported() {
        checkNotSupported("", Stream.of(
            "1.5.0",
            "1.5.0-hello",
            "1.5.0_1",
            "1.5.0_1-hello",
            "1.6.0",
            "1.6.0-hello",
            "1.6.0_42",
            "1.6.0_42-hello"
        ));
    }

    @Test
    void java7AllVersionsSupported() {
        checkSupported("", Stream.of(
            "1.7.0",
            "1.7.0-hello",
            "1.7.0_1",
            "1.7.0_1-hello",
            "1.7.0_241",
            "1.7.0_241-hello"
        ));
    }

    @Test
    void java7HotSpotOnlySupportedAfterUpdate60() {
        checkNotSupported(HOTSPOT_VM_NAME, Stream.of(
            "1.7.0",
            "1.7.0_1",
            "1.7.0-hello",
            "1.7.0_59"
        ));
        checkSupported(HOTSPOT_VM_NAME, Stream.of(
            "1.7.0_60",
            "1.7.0_241",
            "1.7.0_241-hello"
        ));
    }

    @Test
    void java8HotspotOnlySupportedAfterUpdate40() {

        // non-hotspot JVM will be supported by default
        checkSupported("", hotspotJava8NotSupported());
        checkSupported("", hotspotJava8supported());

        checkNotSupported(HOTSPOT_VM_NAME, hotspotJava8NotSupported());
        checkSupported(HOTSPOT_VM_NAME, hotspotJava8supported());
    }

    private Stream<String> hotspotJava8NotSupported() {
        return Stream.of(
            "1.8.0",
            "1.8.0-hello",
            "1.8.0_1",
            "1.8.0_1-hello",
            "1.8.0_39",
            "1.8.0_39-hello"
        );
    }

    private Stream<String> hotspotJava8supported() {
        return Stream.of(
            "1.8.0_40",
            "1.8.0_40-hello",
            "1.8.0_241",
            "1.8.0_241-hello"
        );
    }

    @Test
    void java9AndLaterAllVersionsSupported() {
        checkSupported("", Stream.of(
            "9",
            "9.0.1",
            "9.0.4",
            "10",
            "10.0.1",
            "10.0.2",
            "11",
            "11.0.1",
            "11.0.2",
            "11.0.3",
            "11.0.4",
            "11.0.5",
            "11.0.6",
            "12",
            "12.0.1",
            "12.0.2",
            "13",
            "13.0.1",
            "13.0.2",
            "14")
        );
    }

    @Test
    void shouldBeSupportedInCaseOfParsingError() {
        checkSupported(HOTSPOT_VM_NAME, Stream.of(
            "1.8.0_",
            "1.8.0_aaa"
        ));
    }

    @Test
    void testIbmJava8SupportedAfterBuild2_8() {
        assertThat(AgentMain.isJavaVersionSupported("1.8.0", "IBM J9 VM", "2.8")).isFalse();
        assertThat(AgentMain.isJavaVersionSupported("1.8.0", "IBM J9 VM", "2.9")).isTrue();
    }

    private static void checkSupported(String vmName, Stream<String> versions) {
        versions.forEach((v) -> {
            boolean supported = AgentMain.isJavaVersionSupported(v, vmName, null);
            assertThat(supported)
                .describedAs("java.version = '%s' java.vm.name = '%s' should be supported", v, vmName)
                .isTrue();
        });
    }

    private static void checkNotSupported(String vmName, Stream<String> versions) {
        versions.forEach((v) -> {
            boolean supported = AgentMain.isJavaVersionSupported(v, vmName, null);
            assertThat(supported)
                .describedAs("java.version = '%s' java.vm.name = '%s' should not be supported", v, vmName)
                .isFalse();
        });
    }

}
