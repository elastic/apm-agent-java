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
package co.elastic.apm.agent.tracer.service;

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceInfoTest {

    @Test
    void testNormalizedName() {
        checkServiceInfoEmpty(ServiceInfo.of(""));
        checkServiceInfoEmpty(ServiceInfo.of(" "));

        assertThat(ServiceInfo.of(" a")).isEqualTo(ServiceInfo.of("a"));
        assertThat(ServiceInfo.of(" !web# ")).isEqualTo(ServiceInfo.of("-web-"));
    }

    @Test
    void createEmpty() {
        checkServiceInfoEmpty(ServiceInfo.empty());
        assertThat(ServiceInfo.empty())
            .isEqualTo(ServiceInfo.empty());

    }

    @Test
    void of() {
        checkServiceInfoEmpty(ServiceInfo.of(null));
        checkServiceInfoEmpty(ServiceInfo.of(null, null));

        checkServiceInfo(ServiceInfo.of("service"), "service", null);
        checkServiceInfo(ServiceInfo.of("service", null), "service", null);
        checkServiceInfo(ServiceInfo.of("service", "1.2.3"), "service", "1.2.3");

    }

    @Test
    void checkEquality() {
        checkEquality(ServiceInfo.of(null), ServiceInfo.empty());
        checkEquality(ServiceInfo.of(""), ServiceInfo.empty());
        checkEquality(ServiceInfo.of(null, null), ServiceInfo.empty());
        checkEquality(ServiceInfo.of("", ""), ServiceInfo.empty());
    }

    private static void checkEquality(ServiceInfo first, ServiceInfo second){
        assertThat(first)
            .isEqualTo(second);

        assertThat(first.hashCode())
            .isEqualTo(second.hashCode());
    }

    @Test
    void fromManifest() {
        checkServiceInfoEmpty(ServiceInfo.fromManifest(null));
        checkServiceInfoEmpty(ServiceInfo.fromManifest(null));
        checkServiceInfoEmpty(ServiceInfo.fromManifest(new Manifest()));

        ServiceInfo serviceInfo = ServiceInfo.fromManifest(manifest(Map.of(
            Attributes.Name.IMPLEMENTATION_TITLE.toString(), "service-name"
        )));
        checkServiceInfo(serviceInfo, "service-name", null);

        serviceInfo = ServiceInfo.fromManifest(manifest(Map.of(
            Attributes.Name.IMPLEMENTATION_TITLE.toString(), "my-service",
            Attributes.Name.IMPLEMENTATION_VERSION.toString(), "v42"
        )));
        checkServiceInfo(serviceInfo, "my-service", "v42");
    }

    private static Manifest manifest(Map<String, String> entries) {
        Manifest manifest = new Manifest();

        Attributes attributes = manifest.getMainAttributes();
        entries.forEach(attributes::putValue);

        return manifest;
    }

    private static void checkServiceInfoEmpty(ServiceInfo serviceInfo) {
        assertThat(serviceInfo.isEmpty()).isTrue();
        assertThat(serviceInfo.getServiceName()).isEqualTo("unknown-java-service");
        assertThat(serviceInfo.hasServiceName()).isFalse();
        assertThat(serviceInfo.getServiceVersion()).isNull();

        assertThat(serviceInfo).isEqualTo(ServiceInfo.empty());
    }

    private static void checkServiceInfo(ServiceInfo serviceInfo, String expectedServiceName, @Nullable String expectedServiceVersion) {
        assertThat(serviceInfo.isEmpty()).isFalse();
        assertThat(serviceInfo.getServiceName()).isEqualTo(expectedServiceName);
        assertThat(serviceInfo.hasServiceName()).isTrue();
        if (expectedServiceVersion == null) {
            assertThat(serviceInfo.getServiceVersion()).isNull();
        } else {
            assertThat(serviceInfo.getServiceVersion()).isEqualTo(expectedServiceVersion);
        }

        assertThat(serviceInfo).isEqualTo(ServiceInfo.of(expectedServiceName, expectedServiceVersion));
    }

}
