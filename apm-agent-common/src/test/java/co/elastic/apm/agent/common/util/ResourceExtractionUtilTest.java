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
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
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
    void exportResourceToDirectory(@TempDir Path tmpDir) throws URISyntaxException {
        Path tmp = ResourceExtractionUtil.extractResourceToDirectory("test.txt", "test", ".tmp", tmpDir);

        Path referenceFile = Paths.get(ResourceExtractionUtil.class.getResource("/test.txt").toURI());

        assertThat(tmp).hasSameTextualContentAs(referenceFile);
    }

    @Test
    void exportResourceToDirectoryIdempotence(@TempDir Path tmpDir) throws Exception {
        Path tmp = ResourceExtractionUtil.extractResourceToDirectory("test.txt", "test", ".tmp", tmpDir);
        FileTime created = Files.getLastModifiedTime(tmp);
        Thread.sleep(1000);
        Path after = ResourceExtractionUtil.extractResourceToDirectory("test.txt", "test", ".tmp", tmpDir);
        assertThat(created).isEqualTo(Files.getLastModifiedTime(after));
    }

    @Test
    void testContentDoesNotMatch(@TempDir Path tmpDir) throws Exception {
        Path tmp = ResourceExtractionUtil.extractResourceToDirectory("test.txt", "test", ".tmp", tmpDir);
        Files.writeString(tmp, "changed");
        assertThatThrownBy(() -> ResourceExtractionUtil.extractResourceToDirectory("test.txt", "test", ".tmp", tmpDir))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exportResourceToDirectory_throwExceptionIfNotFound(@TempDir Path tmpDir) {
        assertThatThrownBy(() -> ResourceExtractionUtil.extractResourceToDirectory("nonexist", "nonexist", ".tmp", tmpDir))
            .hasMessage("nonexist not found");
    }

    @Test
    void exportResourceToDirectoryInMultipleThreads(@TempDir Path tmpDir) throws InterruptedException, ExecutionException {
        final int nbThreads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(nbThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(nbThreads);
        final List<Future<Path>> futureList = new ArrayList<>(nbThreads);
        final String tempFileNamePrefix = UUID.randomUUID().toString();

        for (int i = 0; i < nbThreads; i++) {
            futureList.add(executorService.submit(() -> {
                countDownLatch.countDown();
                countDownLatch.await();
                return ResourceExtractionUtil.extractResourceToDirectory("test.txt", tempFileNamePrefix, ".tmp", tmpDir);
            }));
        }

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        for (Future<Path> future : futureList) {
            assertThat(future.get()).isNotNull();
            assertThat(future.get()).exists();
        }
    }

}
