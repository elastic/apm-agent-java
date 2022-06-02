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
package co.elastic.apm.agent.testutils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.util.Objects;
import java.util.function.Consumer;

public class TestContainersUtils {

    private TestContainersUtils() {
    }

    public static Consumer<CreateContainerCmd> withMemoryLimit(int limitMb) {
        return cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(limitMb * 1024 * 1024L);
    }

    public static Slf4jLogConsumer createSlf4jLogConsumer(Class<?> loggerOwner) {
        return new Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger(loggerOwner));
    }
}
