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
package co.elastic.apm.agent.jms.spring;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Session;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Service
public class SpringMapMessageListener implements SessionAwareMessageListener<MapMessage> {

    @Override
    public void onMessage(MapMessage mapMessage, Session session) throws JMSException {
        Map map = ((ActiveMQMapMessage) mapMessage).getContentMap();
        try {
            // Letting Spring kick a receive on another thread to test when concurrent polls are enabled
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Received map message: ");
        map.forEach((key, value) -> System.out.println("   " + key + " --> " + value));
        Transaction transaction = GlobalTracer.get().require(ElasticApmTracer.class).currentTransaction();
        assertThat(transaction.isFinished()).isFalse();
        SpringJmsTest.resultQueue.offer(map);
    }
}
