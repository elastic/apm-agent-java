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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import org.junit.jupiter.api.Test;
import specs.TestJsonSpec;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMetadataProviderTest {

    private static String readProviderMetadataJson(String jsonFile) {
        return TestJsonSpec.getJson(CloudMetadataProviderTest.class, "cloud_metadata/" + jsonFile).toString();
    }

    @Test
    void gcpMetadataDeserializeTest() throws IOException {
        CloudProviderInfo gcpMetadata = CloudMetadataProvider.deserializeGcpMetadata(readProviderMetadataJson("gcp.json"));
        assertThat(gcpMetadata).isNotNull();
        assertThat(gcpMetadata.getProvider()).isEqualTo("gcp");
        assertThat(gcpMetadata.getAvailabilityZone()).isEqualTo("us-west3-a");
        assertThat(gcpMetadata.getRegion()).isEqualTo("us-west3");
        assertThat(gcpMetadata.getInstance().getId()).isEqualTo("4306570268266786072");
        assertThat(gcpMetadata.getInstance().getName()).isEqualTo("basepi-test");
        assertThat(gcpMetadata.getAccount()).isNull();
        assertThat(gcpMetadata.getProject().getId()).isEqualTo("513326162531");
        assertThat(gcpMetadata.getProject().getName()).isEqualTo("elastic-apm");
        assertThat(gcpMetadata.getMachine().getType()).isEqualTo("n1-standard-1");
    }

    @Test
    void awsMetadataDeserializeTest() throws IOException {
        CloudProviderInfo awsMetadata = CloudMetadataProvider.deserializeAwsMetadata(readProviderMetadataJson("aws.json"));
        assertThat(awsMetadata).isNotNull();
        assertThat(awsMetadata.getProvider()).isEqualTo("aws");
        assertThat(awsMetadata.getAvailabilityZone()).isEqualTo("us-east-2a");
        assertThat(awsMetadata.getRegion()).isEqualTo("us-east-2");
        assertThat(awsMetadata.getInstance().getId()).isEqualTo("i-0ae894a7c1c4f2a75");
        assertThat(awsMetadata.getInstance().getName()).isNull();
        assertThat(awsMetadata.getAccount().getId()).isEqualTo("946960629917");
        assertThat(awsMetadata.getProject()).isNull();
        assertThat(awsMetadata.getMachine().getType()).isEqualTo("t2.medium");
    }

    @Test
    void azureMetadataDeserializeTest() throws IOException {
        CloudProviderInfo azureMetadata = CloudMetadataProvider.deserializeAzureMetadata(readProviderMetadataJson("azure.json"));
        assertThat(azureMetadata).isNotNull();
        assertThat(azureMetadata.getProvider()).isEqualTo("azure");
        assertThat(azureMetadata.getAvailabilityZone()).isEqualTo("");
        assertThat(azureMetadata.getRegion()).isEqualTo("westus2");
        assertThat(azureMetadata.getInstance().getId()).isEqualTo("e11ebedc-019d-427f-84dd-56cd4388d3a8");
        assertThat(azureMetadata.getInstance().getName()).isEqualTo("basepi-test");
        assertThat(azureMetadata.getAccount().getId()).isEqualTo("7657426d-c4c3-44ac-88a2-3b2cd59e6dba");
        assertThat(azureMetadata.getProject().getName()).isEqualTo("basepi-testing");
        assertThat(azureMetadata.getProject().getId()).isNull();
        assertThat(azureMetadata.getMachine().getType()).isEqualTo("Standard_D2s_v3");
    }
}
