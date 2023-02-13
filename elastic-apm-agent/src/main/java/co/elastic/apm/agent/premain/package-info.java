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
/**
 * <p>THIS PACKAGE MUST HAVE NO DEPENDENCY ON ANYTHING!</p>
 *
 * It contains the minimal code required to bootstrap the agent without causing any implicit initializations of anything
 * related to the agent, including agent classes loading and linkage.
 * The bootstrap sequence includes:
 * <ul>
 *     <li>
 *         bootstrap checks to abort initialization if required (can be disabled through the
 *         {@code elastic.apm.disable_bootstrap_checks} System property or or the
 *         {@code ELASTIC_APM_DISABLE_BOOTSTRAP_CHECKS} environment variable).
 *     </li>
 *     <li>
 *         load the {@code co.elastic.apm.agent.bci.ElasticApmAgent} class and execute the agent initialization process
 *         <b>through reflection</b>. This can be done synchronously, blocking the bootstrapping thread (default); or
 *         asynchronously on a different thread with the {@code elastic.apm.start_async} System property (since 1.29.0);
 *         or asynchronously on a different thread after some delay, by configuring the
 *         {@code elastic.apm.delay_agent_premain_ms} System property with some positive value.
 *     </li>
 * </ul>
 */
package co.elastic.apm.agent.premain;
