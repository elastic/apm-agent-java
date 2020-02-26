/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.rocketmq.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.Message;

import javax.annotation.Nullable;

public class RocketMQMessageHeaderAccessor implements TextHeaderGetter<Message>, TextHeaderSetter<Message> {

    private static final RocketMQMessageHeaderAccessor INSTANCE = new RocketMQMessageHeaderAccessor();

    public static RocketMQMessageHeaderAccessor getInstance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Message carrier) {
        return carrier.getUserProperty(headerName);
    }

    @Override
    public <S> void forEach(String headerName, Message carrier, S state, HeaderConsumer<String, S> consumer) {
        String headerValue = carrier.getUserProperty(headerName);
        if (!StringUtils.isEmpty(headerName)) {
            consumer.accept(headerValue, state);
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, Message carrier) {
        if (StringUtils.isNoneEmpty(carrier.getProperty(headerName))) {
            carrier.putUserProperty(headerName, headerValue);
        }
    }
}
