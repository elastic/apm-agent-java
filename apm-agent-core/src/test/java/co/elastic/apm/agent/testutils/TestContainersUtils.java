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
package co.elastic.apm.agent.testutils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.testcontainers.DockerClientFactory;

import java.util.Objects;
import java.util.function.Consumer;

public class TestContainersUtils {

    private TestContainersUtils() {
    }

    static {
        // ensure that we have a known and explicit failure when using a buggy docker version
        String versionStr = DockerClientFactory.instance().client().versionCmd().exec().getVersion();
        String[] version = versionStr.split("\\.");
        if (version[0].equals("20") && version[1].equals("10")) {
            Integer patch = Integer.parseInt(version[2]);
            if (patch < 6) {
                throw new IllegalStateException("known issue with docker,using " + versionStr + ", use 19.x or >= 20.10.6, see https://github.com/moby/moby/issues/41820 or https://github.com/testcontainers/testcontainers-java/issues/3613 for details");
            }
        }
    }

    public static Consumer<CreateContainerCmd> withMemoryLimit(int limitMb) {
        return cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(limitMb * 1024 * 1024L);
    }
}
