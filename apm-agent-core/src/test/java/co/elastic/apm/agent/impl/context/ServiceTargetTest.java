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
package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

class ServiceTargetTest {

    @Test
    void createEmpty() {
        ServiceTarget serviceTarget = new ServiceTarget();
        assertThat(serviceTarget).isEmpty();
    }

    @Test
    void typeOnly() {
        ServiceTarget serviceTarget = new ServiceTarget();
        String serviceType = "service-type";

        assertThat(serviceTarget.withType(serviceType))
            .hasType(serviceType)
            .hasNoName()
            .hasDestinationResource(serviceType);

        assertThat(serviceTarget.isDestinationResourceSetByUser()).isFalse();
        assertThat(serviceTarget.hasContent()).isTrue();

        serviceTarget.resetState();
        assertThat(serviceTarget).isEmpty();
    }

    @Test
    void typeAndName() {
        ServiceTarget serviceTarget = new ServiceTarget();

        assertThat(serviceTarget.withType("service-type").withName("service-name"))
            .hasType("service-type")
            .hasName("service-name");


        assertThat(serviceTarget)
            .hasDestinationResource("service-type/service-name");

        assertThat(serviceTarget.getDestinationResource())
            .describedAs("returned value should be cached without modifications")
            .isSameAs(serviceTarget.getDestinationResource());

        assertThat(serviceTarget)
            .hasNotDestinationResourceSetByUser();

        assertThat(serviceTarget.hasContent()).isTrue();

        serviceTarget.resetState();
        assertThat(serviceTarget).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"NULL"}, delimiterString = "|", value = {
        "type/name|type|name",
        "type|type|NULL",
        "/type|/type|NULL",
        "type/|type/|NULL"})
    void copyFromServiceDestinationResource(String destinationResource, @Nullable String expectedType, @Nullable String expectedName) {
        ServiceTarget serviceTarget = new ServiceTarget();

        serviceTarget.copyFromDestinationResource(destinationResource);

        assertThat(serviceTarget).hasType(expectedType);
        if (expectedName == null) {
            assertThat(serviceTarget).hasNoName();
        } else {
            assertThat(serviceTarget).hasName(expectedName);
        }

        assertThat(serviceTarget)
            .hasDestinationResource(destinationResource);

        serviceTarget.resetState();
        assertThat(serviceTarget).isEmpty();
    }

    @Test
    void userResourcePriority() {

        Function<ServiceTarget, ServiceTarget> userSetOperation = st -> st.withUserDestinationResource("user-provided");
        Function<ServiceTarget, ServiceTarget> typeNameOperation = st -> st.withType("type").withName("name");

        testUserResourcePriority(userSetOperation, typeNameOperation);
        testUserResourcePriority(typeNameOperation, userSetOperation);
    }

    private void testUserResourcePriority(Function<ServiceTarget, ServiceTarget>... operations) {
        ServiceTarget serviceTarget = new ServiceTarget();
        Arrays.asList(operations).forEach(o -> o.apply(serviceTarget));

        assertThat(serviceTarget).hasDestinationResource("user-provided");
        assertThat(serviceTarget.isDestinationResourceSetByUser()).isTrue();
    }

    @Test
    void emptyOrNullUserDestinationResourceIsIgnored() {
        ServiceTarget serviceTarget = new ServiceTarget();
        assertThat(serviceTarget.withUserDestinationResource(null)).hasNotDestinationResourceSetByUser();
        assertThat(serviceTarget.withUserDestinationResource("")).hasNotDestinationResourceSetByUser();
    }

    @Test
    void setDestinationResourceFromHostAndPort() {
        ServiceTarget serviceTarget = new ServiceTarget().withType("test").withName("name");
        assertThat(serviceTarget)
            .describedAs("destination resource should be infered from type an name")
            .hasDestinationResource("test/name");

        // host and port is just a convenience to override the default inferred value

        assertThat(serviceTarget.withHostAndPortDestinationResource("hostname", 80))
            .hasDestinationResource("hostname:80");

        assertThat(serviceTarget.withHostAndPortDestinationResource("host-only", -1))
            .hasDestinationResource("host-only");

        assertThat(serviceTarget.withHostAndPortDestinationResource(null, 80))
            .describedAs("null host should be ignored")
            .hasDestinationResource("host-only");

        assertThat(serviceTarget.withHostAndPortDestinationResource("", 80))
            .describedAs("empty host should be ignored")
            .hasDestinationResource("host-only");
    }

    @Test
    void testCopy() {
        testCopy(st -> st.withType("type"),
            ServiceTarget::getType);

        testCopy(st -> st.withType("type").withName("name"),
            ServiceTarget::getType,
            st -> st.getName().toString());

        testCopy(st -> st.withType("type").withName("name").withUserDestinationResource("user-resource"),
            ServiceTarget::getType,
            st -> st.getName().toString(),
            st -> st.getDestinationResource().toString(),
            ServiceTarget::isDestinationResourceSetByUser);

    }

    private void testCopy(Function<ServiceTarget, ServiceTarget> setOperation, Function<ServiceTarget, Object>... getOperations) {
        ServiceTarget original = new ServiceTarget();
        setOperation.apply(original);

        ServiceTarget copy = new ServiceTarget();
        copy.copyFrom(original);

        List<Object> results = new ArrayList<>();
        for (Function<ServiceTarget, Object> operation : getOperations) {
            Object resultOnOriginal = operation.apply(original);
            Object resultOnCopy = operation.apply(copy);
            assertThat(resultOnCopy).isEqualTo(resultOnOriginal);
            results.add(resultOnOriginal);
        }

        original.resetState();
        assertThat(original).isEmpty();

        for (int i = 0; i < getOperations.length; i++) {
            assertThat(getOperations[i].apply(copy))
                .describedAs("copy should keep its state after original was reset")
                .isEqualTo(results.get(i));
        }

    }

    @Test
    void testHostPort() {

    }

}
