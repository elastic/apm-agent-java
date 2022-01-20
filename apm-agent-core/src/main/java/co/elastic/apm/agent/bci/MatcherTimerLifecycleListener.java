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

import co.elastic.apm.agent.bci.bytebuddy.MatcherTimer;
import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

public class MatcherTimerLifecycleListener extends AbstractLifecycleListener {
    private static final Logger logger = LoggerFactory.getLogger(MatcherTimerLifecycleListener.class);

    @Override
    public void stop() {
        if (logger.isDebugEnabled()) {
            final ArrayList<MatcherTimer> matcherTimers = new ArrayList<>(ElasticApmAgent.getMatcherTimers());
            Collections.sort(matcherTimers);
            StringBuilder sb = new StringBuilder()
                .append("Total time spent matching: ").append(String.format("%,d", ElasticApmAgent.getTotalMatcherTime())).append("ns")
                .append('\n')
                .append(MatcherTimer.getTableHeader())
                .append('\n');
            for (MatcherTimer matcherTimer : matcherTimers) {
                sb.append(matcherTimer.toString()).append('\n');
            }
            logger.debug(sb.toString());
        }
    }
}
