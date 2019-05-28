/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.attach;

import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * Successive attachments would lead to the VirtualMachine class to be loaded from multiple class loaders
 * (see net.bytebuddy.agent.ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm#attempt()).
 * That leads to a UnsatisfiedLinkError on Java 7 and 9 because the native library libattach can only be loaded by one class loader.
 * By caching the {@link net.bytebuddy.agent.ByteBuddyAgent.AttachmentProvider.Accessor},
 * the same VirtualMachine class is reused so that there is no attempt to load the libattach library from another class loader.
 */
public class CachedAttachmentProvider implements ByteBuddyAgent.AttachmentProvider {
    private final Accessor accessor;

    public CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider provider) {
        this.accessor = provider.attempt();
        if (accessor == Accessor.Unavailable.INSTANCE) {
            throw new IllegalStateException("Attaching the Elastic APM Java agent is not possible on this JVM. " +
                "For Java 7 and 8 VMs, make sure to use a JDK (not a JRE) so that the tools.jar is present. " +
                "On Java 9 or later, make sure the jdk.attach module is available.");
        }
    }

    @Override
    public Accessor attempt() {
        return accessor;
    }

}
