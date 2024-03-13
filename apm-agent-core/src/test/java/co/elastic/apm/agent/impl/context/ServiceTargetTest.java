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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

class ServiceTargetTest {

    @Test
    void createEmpty() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();
        assertThat(serviceTarget).isEmpty();
        assertThat(serviceTarget).isNotSetByUser();
        assertThat(serviceTarget).hasNoName();
        assertThat(serviceTarget.getType()).isNull();
    }

    @Test
    void typeOnly() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();
        String serviceType = "service-type";

        assertThat(serviceTarget.withType(serviceType))
            .hasType(serviceType)
            .hasNoName()
            .hasDestinationResource(serviceType);

        assertThat(serviceTarget).isNotSetByUser();

        assertThat(serviceTarget.hasContent()).isTrue();

        serviceTarget.resetState();
        assertThat(serviceTarget).isEmpty();
    }

    @Test
    void typeAndName() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();

        assertThat(serviceTarget.withType("service-type").withName("service-name"))
            .hasType("service-type")
            .hasName("service-name");

        assertThat(serviceTarget)
            .hasDestinationResource("service-type/service-name");

        assertThat(serviceTarget)
            .isNotSetByUser();

        assertThat(serviceTarget.hasContent()).isTrue();

        serviceTarget.resetState();
        assertThat(serviceTarget).isEmpty();
    }

    @Test
    void userValuePriority_userValuesAfter() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();

        serviceTarget.withType("type").withName("name");
        assertThat(serviceTarget).hasType("type").hasName("name").isNotSetByUser();

        assertThat(serviceTarget.withUserType("user-type")).hasType("user-type").isSetByUser();
        assertThat(serviceTarget.withUserName("user-name")).hasName("user-name").isSetByUser();
    }

    @Test
    void userValuePriority_userValuesBefore() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();

        assertThat(serviceTarget.withUserType("user-type")).hasType("user-type").isSetByUser();
        assertThat(serviceTarget.withUserName("user-name")).hasName("user-name").isSetByUser();

        // should be ignored
        // once set by user, name should not be overridden unless with 'withUser___' methods
        serviceTarget.withType("type").withName("name").withHostPortName("localhost", 80);

        assertThat(serviceTarget).hasType("user-type").hasName("user-name").isSetByUser();
    }

    @Test
    void userValuePriority_userValuesStillModifiable() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();

        assertThat(serviceTarget.withUserType("user-type")).hasType("user-type").isSetByUser();
        assertThat(serviceTarget.withUserName("user-name")).hasName("user-name").isSetByUser();

        assertThat(serviceTarget.withUserName("another-user-name")).hasName("another-user-name").isSetByUser();
        assertThat(serviceTarget.withUserType("another-user-type")).hasType("another-user-type").isSetByUser();
    }

    @Test
    void emptyOrNullUserDestinationResourceIsIgnored() {
        // using null or empty value should allow user to empty service target
        Stream.of("", null).forEach(value -> {
            ServiceTargetImpl serviceTarget = new ServiceTargetImpl();
            assertThat(serviceTarget.withUserName(value))
                .isEmpty()
                .isSetByUser();
        });

    }

    @Test
    void userResourceWithoutExplicitType() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl();
        serviceTarget.withName("user-resource").withNameOnlyDestinationResource();
        assertThat(serviceTarget)
            .hasName("user-resource")
            .hasDestinationResource("user-resource");
    }

    @Test
    void setDestinationResourceFromHostAndPort() {
        ServiceTargetImpl serviceTarget = new ServiceTargetImpl().withType("test").withName("name");
        assertThat(serviceTarget)
            .describedAs("destination resource should be inferred from type an name")
            .hasDestinationResource("test/name");

        assertThat(serviceTarget.withHostPortName("hostname", 80))
            .hasType("test")
            .hasName("hostname:80")
            .hasDestinationResource("test/hostname:80");

        // keep only name
        serviceTarget.withNameOnlyDestinationResource();

        assertThat(serviceTarget.withHostPortName("hostname", 433))
            .hasName("hostname:433")
            .hasDestinationResource("hostname:433");

        assertThat(serviceTarget.withHostPortName("host-only", -1))
            .hasName("host-only")
            .hasDestinationResource("host-only");

        assertThat(serviceTarget.withHostPortName(null, 80))
            .describedAs("null host should be ignored")
            .hasName("host-only")
            .hasDestinationResource("host-only");

        assertThat(serviceTarget.withHostPortName("", 80))
            .describedAs("empty host should be ignored")
            .hasName("host-only")
            .hasDestinationResource("host-only");
    }

    @Test
    void testCopy() {
        testCopy(st -> st.withType("type"),
            ServiceTargetImpl::getType);

        testCopy(st -> st.withType("type").withName("name"),
            ServiceTargetImpl::getType,
            st -> st.getName().toString());

        testCopy(st -> st.withUserType("user-type").withUserName("user-resource").withNameOnlyDestinationResource(),
            ServiceTargetImpl::getType,
            st -> st.getName().toString(),
            st -> st.getDestinationResource().toString(),
            ServiceTargetImpl::isSetByUser);

    }

    private void testCopy(Function<ServiceTargetImpl, ServiceTargetImpl> setOperation, Function<ServiceTargetImpl, Object>... getOperations) {
        ServiceTargetImpl original = new ServiceTargetImpl();
        setOperation.apply(original);

        ServiceTargetImpl copy = new ServiceTargetImpl();
        copy.copyFrom(original);

        List<Object> results = new ArrayList<>();
        for (Function<ServiceTargetImpl, Object> operation : getOperations) {
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
        ServiceTargetImpl st = new ServiceTargetImpl()
            .withHostPortName("host", 99)
            .withNameOnlyDestinationResource();

        assertThat(st)
            .hasName("host:99")
            .hasDestinationResource("host:99")
            .isNotSetByUser();

        assertThat(st.getType())
            .isNull();

    }

    @Test
    void emptyOrNullUserType() {
        Stream.of("", null).forEach(type -> {
            ServiceTargetImpl st = new ServiceTargetImpl().withUserType(type).withName("name");
            assertThat(st).hasDestinationResource("name");
        });

    }

}
