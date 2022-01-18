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
package co.elastic.apm.agent.scheduled;

import javax.ejb.Schedule;

public class ScheduledTransactionNameInstrumentationTest extends AbstractScheduledTransactionNameInstrumentationTest{
    @Override
    JeeCounter createJeeCounterImpl() {
        return new JeeCounterImpl();
    }

    @Override
    ThrowingCounter createThrowingCounterImpl() {
        return new ThrowingCounterImpl();
    }

    protected static class JeeCounterImpl extends JeeCounter {

        @Schedule(minute = "5")
        public void scheduled() {
            this.count.incrementAndGet();
        }

        @javax.ejb.Schedules({
            @Schedule(minute = "5"),
            @Schedule(minute = "10")
        })
        public void scheduledJava7Repeatable() {
            this.count.incrementAndGet();
        }
    }

    protected static class ThrowingCounterImpl extends ThrowingCounter {

        @Schedule(minute = "5") // whatever the used annotation here, the behavior should be the same
        public void throwingException() {
            count.incrementAndGet();
            throw new RuntimeException("intentional exception");
        }
    }
}
