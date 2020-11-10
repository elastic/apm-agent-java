package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTest {

    private Cloud cloud;

    @BeforeEach
    public void setUp() {
        cloud = new Cloud();
    }

    @Test
    void gcpMetadataDeserializeTest() throws IOException {
        String input = "{\n" +
            "         \"instance\": {\n" +
            "             \"id\": 4306570268266786072,\n" +
            "             \"machineType\": \"projects/513326162531/machineTypes/n1-standard-1\",\n" +
            "             \"name\": \"basepi-test\",\n" +
            "             \"zone\": \"projects/513326162531/zones/us-west3-a\"\n" +
            "         },\n" +
            "         \"project\": {\"numericProjectId\": 513326162531, \"projectId\": \"elastic-apm\"}\n" +
            "     }";


        CloudProviderInfo gcpMetadata = cloud.convertGcpMetadata(input);

        assertThat(gcpMetadata).isNotNull();
        assertThat(gcpMetadata.getProvider()).isEqualTo("gcp");
        assertThat(gcpMetadata.getAvailabilityZone()).isEqualTo("us-west3-a");
        assertThat(gcpMetadata.getRegion()).isEqualTo("us-west3");
        assertThat(gcpMetadata.getInstance().getId()).isEqualTo("4306570268266786072");
        assertThat(gcpMetadata.getInstance().getName()).isEqualTo("basepi-test");
        assertThat(gcpMetadata.getAccount()).isNull();
        assertThat(gcpMetadata.getProject().getId()).isEqualTo("513326162531");
        assertThat(gcpMetadata.getProject().getName()).isEqualTo("elastic-apm");
        assertThat(gcpMetadata.getMachine().getType()).isEqualTo("projects/513326162531/machineTypes/n1-standard-1");
    }
}
