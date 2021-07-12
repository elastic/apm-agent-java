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
package co.elastic.apm.attach;

import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * Successive attachments with a {@code tools.jar}-based {@link net.bytebuddy.agent.ByteBuddyAgent.AttachmentProvider}
 * would lead to the {@link com.sun.tools.attach.VirtualMachine} class to be loaded from multiple class loaders
 * (see {@link net.bytebuddy.agent.ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm#attempt()}).
 * That leads to a UnsatisfiedLinkError on Java 7 and 8 because the native library libattach can only be loaded by one class loader.
 * By caching the {@link net.bytebuddy.agent.ByteBuddyAgent.AttachmentProvider.Accessor},
 * the same VirtualMachine class is reused so that there is no attempt to load the libattach library from another class loader.
 */
public class CachedAttachmentProvider implements ByteBuddyAgent.AttachmentProvider {
    private volatile Accessor accessor;
    private final ByteBuddyAgent.AttachmentProvider delegate;

    CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider delegate) {
        this.accessor = delegate.attempt();
        this.delegate = delegate;
    }

    @Override
    public Accessor attempt() {
        if (accessor == null) {
            accessor = delegate.attempt();
        }
        return accessor;
    }

}
