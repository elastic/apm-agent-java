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
package co.elastic.apm.agent.sdk.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ThreadUtilTest {

    @Test
    public void checkPlatformThreadVirtual() {
        Thread t1 = new Thread();
        assertThat(ThreadUtil.isVirtual(t1)).isFalse();
    }

    @Test
    @DisabledForJreRange(max = JRE.JAVA_20)
    public void checkVirtualThreadVirtual() throws Exception {
        Runnable task = () -> {
        };
        Thread thread = (Thread) Thread.class.getMethod("startVirtualThread", Runnable.class).invoke(null, task);
        assertThat(ThreadUtil.isVirtual(thread)).isTrue();
    }
}
