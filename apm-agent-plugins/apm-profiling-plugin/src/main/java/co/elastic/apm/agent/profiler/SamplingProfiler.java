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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SamplingProfiler implements Runnable, LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfiler.class);

    private final ProfilingConfiguration config;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<Long, CallTree.Root> profiledThreads;
    private final BlockingQueue<ActivationEvent> activationEvents = new LinkedBlockingQueue<>();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    private static class ActivationEvent {
        private TraceContext traceContext;
        @Nullable
        private TraceContext previousContext;
        private long threadId;
        private boolean activation;

        public static ActivationEvent activation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext) {
            return new ActivationEvent(context.getTraceContext().copy(), threadId, true, previousContext != null ? previousContext.getTraceContext() : null);
        }

        public static ActivationEvent deactivation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext) {
            return new ActivationEvent(context.getTraceContext().copy(), threadId, false, previousContext != null ? previousContext.getTraceContext() : null);
        }

        public ActivationEvent(TraceContext traceContext, long threadId, boolean activation, @Nullable TraceContext previousContext) {
            this.traceContext = traceContext;
            this.threadId = threadId;
            this.activation = activation;
            this.previousContext = previousContext;
        }

        public void handle(SamplingProfiler samplingProfiler) {
            if (activation) {
                handleActivationEvent(samplingProfiler);
            } else {
                handleDeactivationEvent(samplingProfiler);
            }
        }

        private void handleActivationEvent(SamplingProfiler samplingProfiler) {
            if (previousContext == null) {
                startProfiling(samplingProfiler);
            } else {
                samplingProfiler.profiledThreads.get(threadId).setActiveSpan(traceContext);
            }
        }

        private void startProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root root = CallTree.createRoot(traceContext.getTraceContext().copy(), samplingProfiler.config.getSampleRate().getMillis(), samplingProfiler.config.getExcludedClasses());
            if (samplingProfiler.profiledThreads.put(threadId, root) != null) {
                logger.warn("Tried to register another profiling root on thread {} for span {}", threadId, traceContext);
            }
        }

        private void handleDeactivationEvent(SamplingProfiler samplingProfiler) {
            if (previousContext == null) {
                stopProfiling(samplingProfiler);
            } else {
                samplingProfiler.profiledThreads.get(threadId).setActiveSpan(previousContext);
            }
        }

        private void stopProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root callTree = samplingProfiler.profiledThreads.get(threadId);
            if (callTree != null && traceContext.getTraceContext().equals(callTree.getTraceContext())) {
                samplingProfiler.profiledThreads.remove(threadId);
                callTree.end();
            } else {
                logger.warn("Tried to stop profiling on a thread or span not active for profiling threadId={}, callTree={}, traceContext={}",
                    threadId, callTree != null ? callTree.getTraceContext() : null, traceContext);
            }
            if (callTree != null) {
                callTree.removeNodesFasterThan(0.01f, 2);
                callTree.spanify();
            }
        }
    }
    public SamplingProfiler(ElasticApmTracer tracer) {
        this(tracer.getConfig(ProfilingConfiguration.class),
            ExecutorUtils.createSingleThreadSchedulingDeamonPool("apm-sampling-profiler", 10),
            new ConcurrentHashMap<Long, CallTree.Root>());
    }

    public SamplingProfiler(ProfilingConfiguration config, ScheduledExecutorService scheduler, ConcurrentMap<Long, CallTree.Root> profiledThreads) {
        this.config = config;
        this.scheduler = scheduler;
        this.profiledThreads = profiledThreads;
    }

    public void onActivation(Thread thread, TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        activationEvents.add(ActivationEvent.activation(activeSpan, thread.getId(), previouslyActive));
    }

    public void onDeactivation(Thread thread, TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        activationEvents.add(ActivationEvent.deactivation(activeSpan, thread.getId(), previouslyActive));
    }

    @Override
    public void run() {
        ArrayList<ActivationEvent> events = new ArrayList<>();
        activationEvents.drainTo(events);
        for (ActivationEvent event : events) {
            event.handle(this);
        }
        long[] profiledThreadIds = getProfiledThreadIds();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(profiledThreadIds, Integer.MAX_VALUE);

        for (int i = 0; i < profiledThreadIds.length; i++) {
            long threadId = profiledThreadIds[i];
            ThreadInfo threadInfo = threadInfos[i];
            if (threadInfo != null) {
                profiledThreads.get(threadId).addStackTrace(Arrays.asList(threadInfo.getStackTrace()));
            }
        }
    }

    private long[] getProfiledThreadIds() {
        long[] result = new long[profiledThreads.size()];
        int i = 0;
        for (Long threadId : profiledThreads.keySet()) {
            result[i++] = threadId;
        }
        return result;
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        scheduler.scheduleAtFixedRate(this, 0, config.getSampleRate().getMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() throws Exception {
        scheduler.shutdownNow();
    }
}
