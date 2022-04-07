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
package co.elastic.apm.api;

import co.elastic.apm.AbstractApiTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


class SpanDestinationApiTest extends AbstractApiTest {

    public static final String INTERNAL_ADDRESS = "internal-address";
    public static final int INTERNAL_PORT = 9001;
    public static final String INTERNAL_RESOURCE = "internal-resource";

    private co.elastic.apm.agent.impl.transaction.Transaction internalTransaction;
    private co.elastic.apm.agent.impl.transaction.Span internalSpan;

    @BeforeEach
    void setUp() {
        reporter.disableCheckDestinationAddress();
        internalTransaction = Objects.requireNonNull(tracer.startRootTransaction(null)).activate();
        internalSpan = Objects.requireNonNull(internalTransaction.createExitSpan())
            .withType("custom")
            .withSubtype("test")
            .activate();
        setDestinationDetailsThroughInternalApi();
    }

    private void setDestinationDetailsThroughInternalApi() {
        internalSpan.getContext().getDestination()
            .withAddress(INTERNAL_ADDRESS)
            .withPort(INTERNAL_PORT);

        internalSpan.getContext().getServiceTarget()
            // using only the type in the resource name
            .withType(INTERNAL_RESOURCE);
    }

    @AfterEach
    void tearDown() {
        internalTransaction.deactivate().end();
    }

    @Test
    void testSetDestinationAddressWithNonNullValues() {
        ElasticApm.currentSpan().setDestinationAddress("address", 80);
        assertDestinationDetails("address", 80, INTERNAL_RESOURCE);
    }

    @Test
    void testInternalSetAddressAfterApiValid() {
        ElasticApm.currentSpan().setDestinationAddress("address", 80);
        setDestinationDetailsThroughInternalApi();
        // Address details set through public API should be preferred even if set before internal setting
        assertDestinationDetails("address", 80, INTERNAL_RESOURCE);
    }

    @Test
    void testSetDestinationAddressWithNegativePort() {
        ElasticApm.currentSpan().setDestinationAddress("address", -1);
        assertDestinationDetails("address", -1, INTERNAL_RESOURCE);
    }

    @Test
    void testInternalSetPortAfterApiInvalidPort() {
        ElasticApm.currentSpan().setDestinationAddress("address", -1);
        setDestinationDetailsThroughInternalApi();
        // using invalid port should unset original setting even if internal used last
        assertDestinationDetails("address", -1, INTERNAL_RESOURCE);
    }

    @Test
    void testSetDestinationAddressWithNullAddress() {
        ElasticApm.currentSpan().setDestinationAddress(null, 80);
        assertDestinationDetails("", 80, INTERNAL_RESOURCE );
    }

    @Test
    void testInternalSetAddressAfterApiInvalidAddress() {
        ElasticApm.currentSpan().setDestinationAddress(null, 80);
        setDestinationDetailsThroughInternalApi();
        assertDestinationDetails("", 80, INTERNAL_RESOURCE);
    }

    @Test
    void testSetDestinationServiceWithNonEmptyValue() {
        ElasticApm.currentSpan().setDestinationService("service-resource");
        assertDestinationDetails(INTERNAL_ADDRESS, INTERNAL_PORT, "service-resource");
    }

    @Test
    void testInternalSetServiceAfterApiValid() {
        ElasticApm.currentSpan().setDestinationService("service-resource");
        setDestinationDetailsThroughInternalApi();
        assertDestinationDetails(INTERNAL_ADDRESS, INTERNAL_PORT, "service-resource");
    }

    @Test
    void testSetDestinationServiceWithNullServiceResource() {
        reporter.disableCheckDestinationService();
        ElasticApm.currentSpan().setDestinationService(null);
        assertDestinationDetails(INTERNAL_ADDRESS, INTERNAL_PORT, "");
    }

    @Test
    void testInternalSetServiceAfterApiNull() {
        reporter.disableCheckDestinationService();
        ElasticApm.currentSpan().setDestinationService(null);
        setDestinationDetailsThroughInternalApi();
        assertDestinationDetails(INTERNAL_ADDRESS, INTERNAL_PORT, "");
    }

    @Test
    void testSetDestinationServiceWithEmptyServiceResource() {
        reporter.disableCheckDestinationService();
        ElasticApm.currentSpan().setDestinationService("");
        assertDestinationDetails(INTERNAL_ADDRESS, INTERNAL_PORT, "");
    }

    private void assertDestinationDetails(String expectedAddress, int expectedPort, String expectedResource) {
        internalSpan.deactivate().end();
        Span span = reporter.getFirstSpan();
        Destination destination = span.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo(expectedAddress);
        assertThat(destination.getPort()).isEqualTo(expectedPort);

        assertThat(span.getContext().getServiceTarget()).hasDestinationResource(expectedResource);
    }
}
