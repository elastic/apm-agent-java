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
package co.elastic.apm.agent.activemq;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;

public class DestinationAddressAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onSend(@Advice.FieldValue("session") ClientSessionInternal session) {
        Span exitSpan = GlobalTracer.get().getActiveExitSpan();
        if (exitSpan == null || exitSpan.getType() != "messaging"
                || exitSpan.getContext().getDestination().getAddress().length() > 0) {
            return;
        }

        String remoteAddress = session.getConnection().getRemoteAddress();
        if (remoteAddress.startsWith("invm:")) {
            return;
        }

        int startOfPort = remoteAddress.lastIndexOf(':');
        int startOfIP = remoteAddress.lastIndexOf('/');
        int endOfIP = startOfPort >= 0 ? startOfPort : remoteAddress.length();

        if (startOfIP >= 0 && startOfIP < endOfIP) {
            String address = remoteAddress.substring(startOfIP + 1, endOfIP);
            exitSpan.getContext().getDestination()
                    .withAddress(address);

            if (startOfPort >= 0) {
                int port = Integer.parseInt(remoteAddress.substring(startOfPort + 1));
                exitSpan.getContext().getDestination()
                        .withPort(port);
            }
        }
    }
}
