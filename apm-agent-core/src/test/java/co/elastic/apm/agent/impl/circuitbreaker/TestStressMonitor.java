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
package co.elastic.apm.agent.impl.circuitbreaker;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

class TestStressMonitor extends StressMonitor {

    private static final Logger logger = LoggerFactory.getLogger(TestStressMonitor.class);

    private volatile boolean stressIndicator;
    private volatile int pollCounter;

    TestStressMonitor(ElasticApmTracer tracer) {
        super(tracer);
    }

    int getPollCount() {
        return pollCounter;
    }

    /**
     * Simulates current stress in the system
     *
     * @return the poll counter at the time indicator had changed state
     */
    synchronized int simulateStress() {
        logger.debug("simulate stress");
        stressIndicator = true;
        return pollCounter;
    }

    /**
     * Simulates relief in stress
     *
     * @return the poll counter at the time indicator had changed state
     */
    synchronized int simulateStressRelieved() {
        logger.debug("simulate stress relief");
        stressIndicator = false;
        return pollCounter;
    }

    @Override
    synchronized boolean isUnderStress() {
        logger.debug("is under stress = {}", stressIndicator);
        pollCounter++;
        return stressIndicator;
    }

    @Override
    synchronized boolean isStressRelieved() {
        pollCounter++;
        return !stressIndicator;
    }

    @Override
    String getStressDetectionInfo() {
        return "Stress is simulated";
    }
}
