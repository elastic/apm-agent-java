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
package co.elastic.apm.agent.rabbitmq;

public final class TestConstants {

    private TestConstants() {}

    public static final String DOCKER_TESTCONTAINER_RABBITMQ_IMAGE = "rabbitmq:3.7-management-alpine";

    public static final String QUEUE_NAME = "spring-boot";

    public static final String TOPIC_EXCHANGE_NAME = "spring-boot-exchange";

    public static final String ROUTING_KEY = "foo.bar.baz";

    public static final String FANOUT_EXCHANGE = "foobar";

    public static final String QUEUE_FOO = "foo";

    public static final String QUEUE_BAR = "bar";;
}
