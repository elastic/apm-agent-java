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
package co.elastic.apm.agent.azurestorage;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.PageRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.bouncycastle.util.Arrays;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AzureStorageInstrumentationIT extends AbstractInstrumentationTest {

    @Container
    public static GenericContainer<?> azurite = new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite:3.15.0")
        .withExposedPorts(10000,10001)
        .withLogConsumer(TestContainersUtils.createSlf4jLogConsumer(AzureStorageInstrumentationIT.class))
        .withStartupTimeout(Duration.ofSeconds(120))
        .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(2048));
    private static int blobPort;
    private Transaction transaction;
    private BlobServiceClient session;

    @BeforeAll
    public static void beforeClass() throws Exception {
        AzureStorageInstrumentationRestProxy.BLOB_HOSTS = Arrays.append(AzureStorageInstrumentationRestProxy.BLOB_HOSTS, "localhost");
        blobPort = azurite.getMappedPort(10000);
    }

    private static BlobServiceClient getSession() {
        return new BlobServiceClientBuilder()
            .connectionString("DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey="+
                "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:"
                + blobPort + "/devstoreaccount1")
            .buildClient();
    }

    @BeforeEach
    void setUp() throws Exception {
        transaction = tracer.startRootTransaction(null).withName("transaction").activate();
        session = getSession();

    }

    @AfterEach
    void tearDown() {
        Optional.ofNullable(transaction).ifPresent(t -> t.deactivate().end());
    }

    @Test
    void Should_Capture_Span_When_Create_Container() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        session.createBlobContainer(containerName);
        reporter.awaitSpanCount(1);
        AssertSpan("Create", containerName, 1);
    }

    @Test
    void Should_Capture_Span_When_Delete_Container() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        session.createBlobContainer(containerName);
        reporter.reset();
        session.deleteBlobContainer(containerName);
        reporter.awaitSpanCount(1);
        AssertSpan("Delete", containerName, 1);
    }

    @Test
    void Should_Capture_Span_When_Create_Page_Blob() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        reporter.reset();
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        blobClient.getPageBlobClient().create(1024);
        reporter.awaitSpanCount(1);
        AssertSpan("Upload", containerName + "/" + blobName, 1);
    }

    @Test
    void Should_Capture_Span_When_Upload_Page_Blob() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        blobClient.getPageBlobClient().create(1024);
        reporter.reset();
        ByteArrayInputStream ba = new ByteArrayInputStream(new byte[512]);
        blobClient.getPageBlobClient().uploadPages(new PageRange().setStart(0).setEnd(511), ba);
        reporter.awaitSpanCount(1);
        AssertSpan("Upload", containerName + "/" + blobName, 1);
    }

    @Test
    void Should_Capture_Span_When_Upload_Block_Blob() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        reporter.reset();
        blobClient.upload(BinaryData.fromString("block blob"));
        reporter.awaitSpanCount(1);
        AssertSpan("Upload", containerName + "/" + blobName, 1);
    }

    @Test
    void Should_Capture_Span_When_Download_Blob() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        blobClient.upload(BinaryData.fromString("block blob"));
        reporter.reset();
        BinaryData bd = blobClient.downloadContent();
        reporter.awaitSpanCount(1);
        AssertSpan("Download", containerName + "/" + blobName, 1);
    }


    @Test
    void Should_Capture_Span_When_Download_Streaming_Blob() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        blobClient.upload(BinaryData.fromString("block blob"));
        reporter.reset();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        blobClient.download(bos);
        reporter.awaitSpanCount(1);
        AssertSpan("Download", containerName + "/" + blobName, 1);
    }

    @Test
    void Should_Capture_Span_When_Delete_Blob() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        blobClient.upload(BinaryData.fromString("block blob"));
        reporter.reset();
        blobClient.delete();
        reporter.awaitSpanCount(1);
        AssertSpan("Delete", containerName + "/" + blobName, 1);
    }

    @Test
    void Should_Capture_Span_When_Copy_From_Uri() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        String blobName = java.util.UUID.randomUUID().toString();
        String blobName2 = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        blobClient.upload(BinaryData.fromString("block blob"));
        reporter.reset();
        BlobClient blobClient2 = blobContainerClient.getBlobClient(blobName2);
        blobClient2.copyFromUrl(blobClient.getBlobUrl());
        reporter.awaitSpanCount(1);
        AssertSpan("Copy", containerName + "/" + blobName2, 1);
    }
    @Test
    void Should_Capture_Span_When_Get_Blobs() throws Exception {
        String containerName = java.util.UUID.randomUUID().toString();
        BlobContainerClient blobContainerClient = session.createBlobContainer(containerName);
        for (int i = 0; i < 2; i++) {

            String blobName = java.util.UUID.randomUUID().toString();
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            blobClient.upload(BinaryData.fromString("block blob"));
        }
        reporter.reset();
        PagedIterable<BlobItem> listBlobs = blobContainerClient.listBlobs();
        for (BlobItem bi : listBlobs) {
            System.out.println(bi.getName());
        }
        reporter.awaitSpanCount(1);
        AssertSpan("ListBlobs", containerName , 1);
    }

    private void AssertSpan(String action, String containerName, int count) {
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(count);
        Span span = reporter.getFirstSpan();

        assertThat(span.getNameAsString()).isEqualTo(AzureStorageHelper.SPAN_NAME + " " + action + " /"+containerName);
        assertThat(span.getType()).isEqualTo(AzureStorageHelper.SPAN_TYPE);
        assertThat(span.getSubtype()).isEqualTo(AzureStorageHelper.SPAN_SUBTYPE);
        assertThat(span.getAction()).isEqualTo(action);
        assertThat(span.getContext().getDestination()).isNotNull();
        Destination destination = span.getContext().getDestination();

        assertThat(destination.getAddress().toString()).isEqualTo("127.0.0.1");
        assertThat(destination.getService().getResource().toString()).isEqualTo("azureblob/devstoreaccount1");
    }

}
