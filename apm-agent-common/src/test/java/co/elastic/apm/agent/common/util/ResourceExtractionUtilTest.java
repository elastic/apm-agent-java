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
package co.elastic.apm.agent.common.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceExtractionUtilTest {

    @Test
    void exportResourceToDirectory() throws URISyntaxException {
        File tmp = ResourceExtractionUtil.extractResourceToTempDirectory("test.txt", UUID.randomUUID().toString(), ".tmp");
        tmp.deleteOnExit();

        Path referenceFile = Paths.get(ResourceExtractionUtil.class.getResource("/test.txt").toURI());

        assertThat(tmp)
            .hasSameTextualContentAs(referenceFile.toFile());
    }

    @Test
    void exportResourceToDirectoryIdempotence() throws InterruptedException {
        String destination = UUID.randomUUID().toString();
        File tmp = ResourceExtractionUtil.extractResourceToTempDirectory("test.txt", destination, ".tmp");
        tmp.deleteOnExit();
        long actual = tmp.lastModified();
        Thread.sleep(1000);
        File after = ResourceExtractionUtil.extractResourceToTempDirectory("test.txt", destination, ".tmp");
        assertThat(actual).isEqualTo(after.lastModified());
    }

    @Test
    void exportResourceToDirectory_throwExceptionIfNotFound() {
        assertThatThrownBy(() -> ResourceExtractionUtil.extractResourceToTempDirectory("nonexist", UUID.randomUUID().toString(), ".tmp")).hasMessage("nonexist not found");
    }

    @Test
    void exportResourceToDirectoryInMultipleThreads() throws InterruptedException, ExecutionException {
        final int nbThreads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(nbThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(nbThreads);
        final List<Future<File>> futureList = new ArrayList<>(nbThreads);
        final String tempFileNamePrefix = UUID.randomUUID().toString();

        for (int i = 0; i < nbThreads; i++) {
            futureList.add(executorService.submit(() -> {
                countDownLatch.countDown();
                countDownLatch.await();
                File file = ResourceExtractionUtil.extractResourceToTempDirectory("test.txt", tempFileNamePrefix, ".tmp");
                file.deleteOnExit();
                return file;
            }));
        }

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        for (Future<File> future : futureList) {
            assertThat(future.get()).isNotNull();
            assertThat(future.get()).exists();
        }
    }

}
