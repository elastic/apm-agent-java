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
package co.elastic.apm.attach;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashMap;

/**
 * Note this is used for integration testing by the core project,
 * so don't delete it without running tests there! (It's here
 * to avoid a cyclic dependency in the poms)
 */
public class ExampleSelfAttachAppWithProvidedJar {

    public static void main(String[] args) throws InterruptedException {
        // Just sleep for 5 minutes then exit
        //long pid = ProcessHandle.current().pid(); //java 9+
        //Use the old hack - doesn't need to be guaranteed all platforms, it's just for testing
        String pidHost = ManagementFactory.getRuntimeMXBean().getName();
        long pid = Integer.parseInt(pidHost.substring(0,pidHost.indexOf('@')));
        ElasticApmAttacher.attach(""+pid, new HashMap<String,String>(), new File(System.getProperty("ElasticApmAgent.jarfile")));
        Thread.sleep(5*60*1000);
    }
}
