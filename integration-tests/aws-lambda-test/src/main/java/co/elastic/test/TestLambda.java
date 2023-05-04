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
package co.elastic.test;

import co.elastic.apm.api.ElasticApm;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class TestLambda implements RequestHandler<String, String> {
    @Override
    public String handleRequest(String command, Context context) {
        ElasticApm.currentTransaction().addCustomContext("command", command);
        if (command.startsWith("sleep ")) {
            long millis = Long.parseLong(command.substring("sleep ".length()));
            doSleep(millis);
            return "slept " + millis;
        } else if ("die".equals(command)) {
            System.exit(-42);
        } else if ("flush".equals(command)) {
            return "";
        }
        throw new IllegalArgumentException("Unknown command: " + command);
    }

    private static void doSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
