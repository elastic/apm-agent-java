/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HelperClassManagerTest {

    @Test
    void testLoadHelperClass() {
        final HelperClassManager<HelperClassInterface> helperClassManager = HelperClassManager.ForSingleClassLoader.of(mock(ElasticApmTracer.class),
            "co.elastic.apm.agent.bci.HelperClassManagerTest$HelperClassImpl",
            "co.elastic.apm.agent.bci.HelperClassManagerTest$AdditionalHelper");

        final HelperClassInterface helper = helperClassManager.getForClassLoaderOfClass(HelperClassManagerTest.class);
        assertThat(helper.helpMe()).isTrue();
        assertThat(helper.getClass().getClassLoader().getParent()).isEqualTo(HelperClassManagerTest.class.getClassLoader());
    }

    public interface HelperClassInterface {
        boolean helpMe();
    }

    public static class HelperClassImpl implements HelperClassInterface {

        @Override
        public boolean helpMe() {
            return new AdditionalHelper().helpMe2();
        }
    }

    public static class AdditionalHelper {

        public boolean helpMe2() {
            return true;
        }
    }
}
