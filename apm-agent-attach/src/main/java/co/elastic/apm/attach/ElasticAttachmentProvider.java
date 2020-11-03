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
package co.elastic.apm.attach;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.util.ArrayList;
import java.util.Arrays;

public class ElasticAttachmentProvider {

    private static ByteBuddyAgent.AttachmentProvider provider;

    /**
     * Initializes attachment provider, this method can only be called once as it loads native code.
     *
     * @param useEmulatedAttach {@literal true} to enable emulated attach, {@literal false} to disable
     */
    public synchronized static void init(boolean useEmulatedAttach) {
        if (provider != null) {
            throw new IllegalStateException("ElasticAttachmentProvider.init() should only be called once");
        }

        ArrayList<ByteBuddyAgent.AttachmentProvider> providers = new ArrayList<>();
        if (useEmulatedAttach) {
            providers.add(ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE);
        }
        providers.addAll(Arrays.asList(ByteBuddyAgent.AttachmentProvider.ForModularizedVm.INSTANCE,
            ByteBuddyAgent.AttachmentProvider.ForJ9Vm.INSTANCE,
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JVM_ROOT),
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JDK_ROOT),
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.MACINTOSH),
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForUserDefinedToolsJar.INSTANCE)));

        provider = new ByteBuddyAgent.AttachmentProvider.Compound(providers);
    }

    /**
     * Get (and optionally initialize) attachment provider, will internally call {@link #init(boolean)} if not already called
     *
     * @return attachment provider
     */
    public synchronized static ByteBuddyAgent.AttachmentProvider get() {
        if (provider != null) {
            return provider;
        }
        init(true);
        return provider;
    }
}
