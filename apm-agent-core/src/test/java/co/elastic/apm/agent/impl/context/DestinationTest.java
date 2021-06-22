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
package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationTest {

    @Test
    void ipV6AddressNormalized() {
        checkSetAddress("::1", "::1");
        checkSetAddress("[::1]", "::1");
    }

    @Test
    void setAddress() {
        checkSetAddress("hostname", "hostname");
    }

    @Test
    void setHostAndPort() {
        // since it's manual parsing, we'd better check corner cases
        checkSetHostAndPort("host:7", "host", 7);
        checkSetHostAndPort("host:42", "host", 42);

        checkSetHostAndPort("proxy:3128", "proxy", 3128);
        checkSetHostAndPort("[::1]:4242", "::1", 4242);
    }

    @Test
    void invalidPortShouldIgnore() {
        checkInvalidHostAndPort("toto:-1"); // invalid character in port
        checkInvalidHostAndPort("a:b"); // non numeric port
        checkInvalidHostAndPort("c;0"); // missing port separator
    }

    @Test
    void setAddressTwiceShouldReset() {
        Destination destination = new Destination();

        assertThat(destination.withAddress("aaa").withAddress("bb").getAddress().toString())
            .isEqualTo("bb");
    }

    @Test
    void userAddressHavePriority() {
        checkAddress("user-before", d -> d.withUserAddress("user-before").withAddress("address"));
        checkAddress("user-after", d -> d.withAddress("user-before").withUserAddress("user-after"));

        Destination emptyUserAddress = new Destination().withAddress("host").withUserAddress("");
        assertThat(emptyUserAddress.getAddress()).isEmpty();
        assertThat(emptyUserAddress.hasContent()).isFalse();
    }

    @Test
    void userPortHavePriority() {
        checkPort(42, d -> d.withPort(42));
        checkPort(33, d -> d.withUserPort(33));
        checkPort(33, d -> d.withPort(42).withUserPort(33));
        checkPort(33, d -> d.withUserPort(33).withPort(42));
    }

    @Test
    void userServiceResourceHavePriority() {
        checkServiceResource("resource", s -> s.withResource("resource"));
        checkServiceResource("user-resource", s -> s.withResource("resource").withUserResource("user-resource"));
        checkServiceResource("user-resource", s -> s.withUserResource("user-resource").withResource("resource"));

        Destination.Service emptyService = new Destination.Service().withResource("resource").withUserResource("");
        assertThat(emptyService.getResource()).isEmpty();
        assertThat(emptyService.hasContent()).isFalse();
    }

    private void checkServiceResource(String expected, Function<Destination.Service, Destination.Service> operations) {
        Destination.Service service = new Destination.Service();
        operations.apply(service);
        assertThat(service.getResource().toString())
            .isEqualTo(expected);

        assertThat(service.hasContent()).isTrue();
    }

    private static void checkPort(int expected,Function<Destination,Destination> operations ){
        Destination destination = new Destination();
        operations.apply(destination);
        assertThat(destination.getPort()).isEqualTo(expected);

        assertThat(destination.hasContent()).isTrue();
    }

    private static void checkAddress(String expected, Function<Destination,Destination> operations){
        Destination destination = new Destination();
        operations.apply(destination);
        assertThat(destination.getAddress().toString()) // call to toString required otherwise comparison fails
            .isEqualTo(expected);

        assertThat(destination.hasContent()).isTrue();
    }

    private void checkSetAddress(String input, String expectedAddress) {
        checkAddress(expectedAddress, d -> d.withAddress(input));
    }

    private void checkSetHostAndPort(String input, String expectedHost, int expectedPort) {
        Destination destination = new Destination();
        destination.withAddressPort(input);
        assertThat(destination.hasContent()).isTrue();

        assertThat(destination.getPort()).isEqualTo(expectedPort);
        assertThat(destination.getAddress().toString())
            .isEqualTo(expectedHost);
    }

    private void checkInvalidHostAndPort(String input) {
        Destination destination = new Destination();
        destination.withAddressPort(input);

        assertThat(destination.hasContent()).isFalse();
        assertThat(destination.getPort()).isZero();
        assertThat(destination.getAddress()).isEmpty();
    }

}
