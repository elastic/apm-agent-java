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
package co.elastic.apm.agent.dubbo.helper;

import com.alibaba.dubbo.rpc.Invocation;

import javax.annotation.Nullable;

public class AlibabaDubboAttachmentHelperImpl implements AlibabaDubboAttachmentHelper {

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Invocation invocation) {
        return invocation.getAttachment(headerName);
    }

    @Override
    public <S> void forEach(String headerName, Invocation invocation, S state, HeaderConsumer<String, S> consumer) {
        consumer.accept(invocation.getAttachment(headerName), state);
    }

    @Override
    public void setHeader(String headerName, String headerValue, Invocation invocation) {
        invocation.getAttachments().put(headerName, headerValue);
    }
}
