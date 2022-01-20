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

import java.util.Arrays;
import java.util.List;

public class ElasticAttachmentProvider {

    private static ByteBuddyAgent.AttachmentProvider provider;

    private static ByteBuddyAgent.AttachmentProvider fallback;

    /**
     * Initializes attachment provider, this method can only be called once as it loads native code.
     */
    private synchronized static void init() {
        if (provider != null) {
            throw new IllegalStateException("ElasticAttachmentProvider.init() should only be called once");
        }

        List<ByteBuddyAgent.AttachmentProvider> providers = Arrays.asList(
            ByteBuddyAgent.AttachmentProvider.ForModularizedVm.INSTANCE,
            ByteBuddyAgent.AttachmentProvider.ForJ9Vm.INSTANCE,
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JVM_ROOT),
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JDK_ROOT),
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.MACINTOSH),
            new CachedAttachmentProvider(ByteBuddyAgent.AttachmentProvider.ForUserDefinedToolsJar.INSTANCE),
            // only use emulated attach last, as native attachment providers should be preferred
            getFallback());


        provider = new ByteBuddyAgent.AttachmentProvider.Compound(providers);
    }

    private synchronized static void initFallback(){
        if (fallback != null) {
            throw new IllegalStateException("ElasticAttachmentProvider.initFallback() should only be called once");
        }
        fallback = ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE;
    }

    /**
     * Get (and optionally initialize) attachment provider
     *
     * @return attachment provider
     */
    public synchronized static ByteBuddyAgent.AttachmentProvider get() {
        if (provider == null) {
            init();
        }
        return provider;
    }

    /**
     * Get (and optionally initialize) fallback (emulated) attachment provider
     *
     * @return fallback (emulated) attachment provider
     */
    public synchronized static ByteBuddyAgent.AttachmentProvider getFallback() {
        if (fallback == null) {
            initFallback();
        }
        return fallback;
    }

}
