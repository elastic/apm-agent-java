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
package co.elastic.apm.agent.springwebmvc.exception.testapp.exception_handler;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/exception-handler")
public class ExceptionHandlerController {

    @GetMapping("/throw-exception")
    public ResponseEntity throwException() {
        throw new ExceptionHandlerRuntimeException("runtime exception occurred");
    }

    @GetMapping("/throw-exception-sc200")
    public ResponseEntity throwExceptionStatusCode200() {
        throw new ExceptionHandlerRuntimeException200("runtime exception occurred");
    }

    @ExceptionHandler({ExceptionHandlerRuntimeException.class})
    public ResponseEntity handleException(Exception e) {
        // handle exception
        return new ResponseEntity("exception-handler " + e.getMessage(), new LinkedMultiValueMap<>(), 409);
    }

    @ExceptionHandler({ExceptionHandlerRuntimeException200.class})
    public ResponseEntity handleExceptionStatusCode200(Exception e) {
        // handle exception
        return new ResponseEntity("exception-handler " + e.getMessage(), new LinkedMultiValueMap<>(), 200);
    }
}
