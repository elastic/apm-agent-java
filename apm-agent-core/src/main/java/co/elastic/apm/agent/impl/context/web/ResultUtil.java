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
package co.elastic.apm.agent.impl.context.web;

import co.elastic.apm.agent.tracer.Outcome;

import javax.annotation.Nullable;

public class ResultUtil {

    @Nullable
    public static String getResultByHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return "HTTP 2xx";
        }
        if (status >= 300 && status < 400) {
            return "HTTP 3xx";
        }
        if (status >= 400 && status < 500) {
            return "HTTP 4xx";
        }
        if (status >= 500 && status < 600) {
            return "HTTP 5xx";
        }
        if (status >= 100 && status < 200) {
            return "HTTP 1xx";
        }
        return null;
    }

    public static Outcome getOutcomeByHttpClientStatus(int status) {
        if (status < 100 || status >= 600) {
            return Outcome.FAILURE;
        }
        return status < 400 ? Outcome.SUCCESS : Outcome.FAILURE;
    }

    public static Outcome getOutcomeByHttpServerStatus(int status) {
        if (status < 100 || status >= 600) {
            return Outcome.FAILURE;
        }
        return status < 500 ? Outcome.SUCCESS : Outcome.FAILURE;
    }
}
