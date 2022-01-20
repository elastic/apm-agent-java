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
package co.elastic.apm.plugin.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class TestClass {

    private static final Logger logger = LoggerFactory.getLogger(TestClass.class);

    public void traceMe(boolean throwException) throws IllegalStateException {

        // Testing that usage of slf4j in instrumented library doesn't break anything and that log correlation works
        String infoMessage = String.format("TestClass#traceMe was called, transaction ID: %s, trace ID: %s", MDC.get("transaction.id"), MDC.get("trace.id"));
        logger.info(infoMessage);
        System.out.println(infoMessage);

        if (throwException) {
            throw new IllegalStateException("Test Exception");
        }
    }
}
