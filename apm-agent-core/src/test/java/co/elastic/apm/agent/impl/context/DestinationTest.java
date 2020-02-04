package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

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
        checkSetHostAndPort("proxy:3128", "proxy", 3128);
        checkSetHostAndPort("[::1]:4242", "::1", 4242);
    }

    @Test
    void setAddressTwiceShouldReset() {
        Destination destination = new Destination();

        assertThat(destination.withAddress("aaa").withAddress("bb").getAddress().toString())
            .isEqualTo("bb");

    }

    private void checkSetAddress(String input, String expectedAddress){
        Destination destination = new Destination();
        destination.withAddress(input);

        assertThat(destination.getAddress().toString()) // call to toString required otherwise comparison fails
            .isEqualTo(expectedAddress);
    }

    private void checkSetHostAndPort(String input, String expectedHost, int expectedPort) {
        Destination destination = new Destination();
        destination.withAddressPort(input);

        assertThat(destination.getPort()).isEqualTo(expectedPort);
        assertThat(destination.getAddress().toString())
            .isEqualTo(expectedHost);
    }

}
