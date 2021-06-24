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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.premain.BootstrapCheck;
import co.elastic.apm.agent.premain.VerifyNoneBootstrapCheck;
import org.junit.jupiter.api.Test;

import java.lang.management.RuntimeMXBean;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifyNoneBootstrapCheckTest {

    private final BootstrapCheck.BootstrapCheckResult result = new BootstrapCheck.BootstrapCheckResult();

    @Test
    void testNoverify() {
        RuntimeMXBean bean = mock(RuntimeMXBean.class);
        when(bean.getInputArguments()).thenReturn(Collections.singletonList("-noverify"));
        VerifyNoneBootstrapCheck check = new VerifyNoneBootstrapCheck(bean);
        check.doBootstrapCheck(result);
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void testVerifyNone() {
        RuntimeMXBean bean = mock(RuntimeMXBean.class);
        when(bean.getInputArguments()).thenReturn(Collections.singletonList("-Xverify:none"));
        VerifyNoneBootstrapCheck check = new VerifyNoneBootstrapCheck(bean);
        check.doBootstrapCheck(result);
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void testVerifyAll() {
        RuntimeMXBean bean = mock(RuntimeMXBean.class);
        when(bean.getInputArguments()).thenReturn(Collections.singletonList("-Xverify:all"));
        VerifyNoneBootstrapCheck check = new VerifyNoneBootstrapCheck(bean);
        check.doBootstrapCheck(result);
        assertThat(result.isEmpty()).isTrue();
    }
}
