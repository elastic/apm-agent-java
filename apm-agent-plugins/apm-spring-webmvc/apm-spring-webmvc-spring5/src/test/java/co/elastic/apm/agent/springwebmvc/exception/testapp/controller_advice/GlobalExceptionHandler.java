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
package co.elastic.apm.agent.springwebmvc.exception.testapp.controller_advice;

import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ControllerAdviceRuntimeException.class)
    public ResponseEntity<?> globalExceptionHandler(ControllerAdviceRuntimeException ex) {
        return new ResponseEntity<>("controller-advice " + ex.getMessage(), new LinkedMultiValueMap<>(), 409);
    }

    @ExceptionHandler(ControllerAdviceRuntimeException200.class)
    public ResponseEntity<?> globalExceptionHandlerStatusCode200(ControllerAdviceRuntimeException200 ex) {
        return new ResponseEntity<>("controller-advice " + ex.getMessage(), new LinkedMultiValueMap<>(), 200);
    }
}
