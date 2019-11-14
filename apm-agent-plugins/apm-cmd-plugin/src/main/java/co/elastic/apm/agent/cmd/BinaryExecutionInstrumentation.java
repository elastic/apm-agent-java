/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.cmd;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;

import java.util.Collection;
import java.util.Collections;

// probably not worth having a class hierarchy for this
// having distinct instrumentation for commons-exec and java standard lib should improve clarity
// -> does that means having separate plugins ? would somehow make sense

// how do we prevent double instrumentation both at commons-exec and standard lib level as it is likely using
// the same entry point in the JDK ?
// it's based on java.lang.Process class under the hood

// j.l.Process{,Builder} -> Process for execution
// Runtime.exec(...) -> Process for execution (at least in java11)

// question: why not instrument "Process" in the general case
// start span when calling Process.waitFor(), end span when returning
// abrupt termination on destroy/destroyForcibly (waitFor should probably throw an exception in this case)
public abstract class BinaryExecutionInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("execute");
    }
}
