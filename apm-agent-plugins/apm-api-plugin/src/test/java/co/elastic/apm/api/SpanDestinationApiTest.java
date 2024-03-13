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
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

class SpanDestinationApiTest extends AbstractApiTest {

    public static final String INTERNAL_ADDRESS = "internal-address";
    public static final int INTERNAL_PORT = 9001;
    public static final String INTERNAL_RESOURCE = "internal-resource";

    private TransactionImpl internalTransaction;
    private SpanImpl internalSpan;

    @BeforeEach
    void setUp() {

        reporter.disableCheckDestinationAddress();
        internalTransaction = Objects.requireNonNull(tracer.startRootTransaction(null)).activate();
        internalSpan = Objects.requireNonNull(internalTransaction.createExitSpan())
            .withType("custom")
            .withSubtype("test")
            .activate();

        setDestinationDetailsThroughInternalApi();

        // test defaults set from internal API
        assertThat(internalSpan.getContext().getServiceTarget())
            .hasDestinationResource(INTERNAL_RESOURCE);
        assertThat(internalSpan.getContext().getDestination())
            .hasAddress(INTERNAL_ADDRESS)
            .hasPort(INTERNAL_PORT);
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

        assertThat(getSpan().getContext().getDestination()).hasAddress("address").hasPort(80);
    }

    @Test
    void testInternalSetAddressAfterApiValid() {
        ElasticApm.currentSpan().setDestinationAddress("address", 80);
        setDestinationDetailsThroughInternalApi();
        // Address details set through public API should be preferred even if set before internal setting
        assertThat(getSpan().getContext().getDestination()).hasAddress("address").hasPort(80);
    }

    @Test
    void testSetDestinationAddressWithNegativePort() {
        ElasticApm.currentSpan().setDestinationAddress("address", -1);
        assertThat(getSpan().getContext().getDestination()).hasAddress("address").hasNoPort();
    }

    @Test
    void testInternalSetPortAfterApiInvalidPort() {
        ElasticApm.currentSpan().setDestinationAddress("address", -1);
        setDestinationDetailsThroughInternalApi();
        // using invalid port should unset original setting even if internal used last
        assertThat(getSpan().getContext().getDestination()).hasAddress("address").hasNoPort();
    }

    @Test
    void testSetDestinationAddressWithNullAddress() {
        ElasticApm.currentSpan().setDestinationAddress(null, 80);
        assertThat(getSpan().getContext().getDestination()).hasEmptyAddress().hasPort(80);
    }

    @Test
    void testInternalSetAddressAfterApiInvalidAddress() {
        ElasticApm.currentSpan().setDestinationAddress(null, 80);
        setDestinationDetailsThroughInternalApi();
        assertThat(getSpan().getContext().getDestination()).hasEmptyAddress().hasPort(80);
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testSetDestinationServiceWithNonEmptyValue() {
        ElasticApm.currentSpan().setDestinationService("service-resource");

        assertThat(getSpan().getContext().getServiceTarget())
            .isSetByUser()
            .hasType("internal-resource") // should reuse the already set value
            .hasName("service-resource")
            .hasDestinationResource("service-resource");
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testInternalSetServiceAfterApiValid() {
        ElasticApm.currentSpan().setDestinationService("service-resource");
        setDestinationDetailsThroughInternalApi();
        assertThat(getSpan().getContext().getServiceTarget()).isSetByUser().hasDestinationResource("service-resource");
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testSetDestinationServiceWithNullServiceResource() {
        // opt-out of service target check as internal plugins have to set it for exit spans
        reporter.disableCheckServiceTarget();

        ElasticApm.currentSpan().setDestinationService(null);
        assertThat(getSpan().getContext().getServiceTarget()).isSetByUser().isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testInternalSetServiceAfterApiNull() {
        // opt-out of service target check as internal plugins have to set it for exit spans
        reporter.disableCheckServiceTarget();

        ElasticApm.currentSpan().setDestinationService(null);
        setDestinationDetailsThroughInternalApi();
        assertThat(getSpan().getContext().getServiceTarget()).isSetByUser().isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testSetDestinationServiceWithEmptyServiceResource() {
        // opt-out of service target check as internal plugins have to set it for exit spans
        reporter.disableCheckServiceTarget();

        ElasticApm.currentSpan().setDestinationService("");
        assertThat(getSpan().getContext().getServiceTarget()).isSetByUser().isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testSetServiceResource() {

        // test implementation detail: we have to discard service target state otherwise type is already set
        ElasticApm.currentSpan().setDestinationService(null);

        ElasticApm.currentSpan().setDestinationService("my-service");
        assertThat(getSpan().getContext().getServiceTarget())
            .isSetByUser()
            .hasType("") // using an empty type for calls to the legacy API.
            .hasName("my-service")
            .hasDestinationResource("my-service");
    }

    @Test
    void testSetServiceTargetTypeAndName() {
        ElasticApm.currentSpan().setServiceTarget("my-type", "my-name");
        assertThat(getSpan().getContext().getServiceTarget())
            .isSetByUser()
            .hasType("my-type")
            .hasName("my-name")
            .hasDestinationResource("my-type/my-name"); // default format unless using destination resource
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated API
    void testSetServiceTargetTypeNameAndServiceResource() {
        ElasticApm.currentSpan().setServiceTarget("my-type", "my-name").setDestinationService("my-resource");
        assertThat(getSpan().getContext().getServiceTarget())
            .isSetByUser()
            .hasType("my-type")
            .hasName("my-resource") // in this case the original name is overridden as we store resource in name
            .hasDestinationResource("my-resource");
    }

    private SpanImpl getSpan() {
        internalSpan.deactivate().end();
        return reporter.getFirstSpan();
    }

}
