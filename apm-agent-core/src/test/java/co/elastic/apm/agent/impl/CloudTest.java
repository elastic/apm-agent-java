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

    @Test
    void awsMetadataDeserializeTest() throws IOException {
        String input = "{\n" +
            "    \"accountId\": \"946960629917\",\n" +
            "    \"architecture\": \"x86_64\",\n" +
            "    \"availabilityZone\": \"us-east-2a\",\n" +
            "    \"billingProducts\": null,\n" +
            "    \"devpayProductCodes\": null,\n" +
            "    \"marketplaceProductCodes\": null,\n" +
            "    \"imageId\": \"ami-07c1207a9d40bc3bd\",\n" +
            "    \"instanceId\": \"i-0ae894a7c1c4f2a75\",\n" +
            "    \"instanceType\": \"t2.medium\",\n" +
            "    \"kernelId\": null,\n" +
            "    \"pendingTime\": \"2020-06-12T17:46:09Z\",\n" +
            "    \"privateIp\": \"172.31.0.212\",\n" +
            "    \"ramdiskId\": null,\n" +
            "    \"region\": \"us-east-2\",\n" +
            "    \"version\": \"2017-09-30\"\n" +
            "}";

        CloudProviderInfo gcpMetadata = cloud.convertAwsMetadata(input);

        assertThat(gcpMetadata).isNotNull();
        assertThat(gcpMetadata.getProvider()).isEqualTo("aws");
        assertThat(gcpMetadata.getAvailabilityZone()).isEqualTo("us-east-2a");
        assertThat(gcpMetadata.getRegion()).isEqualTo("us-east-2");
        assertThat(gcpMetadata.getInstance().getId()).isEqualTo("i-0ae894a7c1c4f2a75");
        assertThat(gcpMetadata.getInstance().getName()).isNull();
        assertThat(gcpMetadata.getAccount().getId()).isEqualTo("946960629917");
        assertThat(gcpMetadata.getProject()).isNull();
        assertThat(gcpMetadata.getMachine().getType()).isEqualTo("t2.medium");
    }

    @Test
    void azureMetadataDeserializeTest() throws IOException {
        String input = "{\n" +
            "    \"location\": \"westus2\",\n" +
            "    \"name\": \"basepi-test\",\n" +
            "    \"resourceGroupName\": \"basepi-testing\",\n" +
            "    \"subscriptionId\": \"7657426d-c4c3-44ac-88a2-3b2cd59e6dba\",\n" +
            "    \"vmId\": \"e11ebedc-019d-427f-84dd-56cd4388d3a8\",\n" +
            "    \"vmScaleSetName\": \"\",\n" +
            "    \"vmSize\": \"Standard_D2s_v3\",\n" +
            "    \"zone\": \"\"\n" +
            "}";

        CloudProviderInfo gcpMetadata = cloud.convertAzureMetadata(input);

        assertThat(gcpMetadata).isNotNull();
        assertThat(gcpMetadata.getProvider()).isEqualTo("azure");
        assertThat(gcpMetadata.getAvailabilityZone()).isEqualTo("");
        assertThat(gcpMetadata.getRegion()).isEqualTo("westus2");
        assertThat(gcpMetadata.getInstance().getId()).isEqualTo("e11ebedc-019d-427f-84dd-56cd4388d3a8");
        assertThat(gcpMetadata.getInstance().getName()).isEqualTo("basepi-test");
        assertThat(gcpMetadata.getAccount().getId()).isEqualTo("7657426d-c4c3-44ac-88a2-3b2cd59e6dba");
        assertThat(gcpMetadata.getProject().getName()).isEqualTo("basepi-testing");
        assertThat(gcpMetadata.getProject().getId()).isNull();
        assertThat(gcpMetadata.getMachine().getType()).isEqualTo("Standard_D2s_v3");
    }
}
