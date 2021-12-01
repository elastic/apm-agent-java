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
package co.elastic.apm.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.event.AbortProcessingException;
import jakarta.faces.event.ComponentSystemEvent;
import jakarta.inject.Named;

@Named
@ApplicationScoped
public class User {

    private String name;

    public String getName() {
        System.out.println("getName: " + name);
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Both process and process2 are valid method signatures
    public void process(ComponentSystemEvent event) throws AbortProcessingException {
        System.out.println("process called");
        name = name.toUpperCase();
    }

    public void process2() {
        System.out.println("process2 called");
        name = name.toUpperCase();
    }
}
