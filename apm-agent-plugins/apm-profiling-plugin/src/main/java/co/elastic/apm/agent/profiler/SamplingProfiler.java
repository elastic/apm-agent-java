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
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SamplingProfiler implements Runnable, LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfiler.class);

    private final ProfilingConfiguration config;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<Long, CallTree.Root> profiledThreads;
    // TODO limit the queue size
    //  how do we deal with lost events?
    private final MessagePassingQueue<ActivationEvent> activationEvents = new MpscUnboundedArrayQueue<ActivationEvent>(256);
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private volatile boolean profilingSessionOngoing = false;

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
        if (profilingSessionOngoing) {
            activationEvents.offer(ActivationEvent.activation(activeSpan, thread.getId(), previouslyActive));
        }
    }

    public void onDeactivation(Thread thread, TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        if (profilingSessionOngoing) {
            activationEvents.offer(ActivationEvent.deactivation(activeSpan, thread.getId(), previouslyActive));
        }
    }

    @Override
    public void run() {
        // the sample rate should be consistent for a given profiling session
        long sampleRate = config.getSampleRate().getMillis();
        long deadline = config.getProfilingDuration().getMillis() + System.currentTimeMillis();
        profilingSessionOngoing = true;
        while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() < deadline) {
            try {
                long startNs = System.nanoTime();

                takeThreadSnapshot();

                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                logger.trace("Taking snapshot took {}ms", durationMs);

                Thread.sleep(Math.max(sampleRate - durationMs, 0));
            } catch (RuntimeException e) {
                logger.error("Exception while taking profiling snapshot", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (config.getProfilingDelay().getMillis() != 0) {
            profilingSessionOngoing = false;
            // TODO do we want to create inferred spans for partially profiled transactions?
            profiledThreads.clear();
        }
        // clears the interrupted status so that the thread can return to the pool
        if (!Thread.interrupted()) {
            scheduler.schedule(this, config.getProfilingDelay().getMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void takeThreadSnapshot() {
        processActivationEventsUpTo(System.nanoTime());
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

    private void processActivationEventsUpTo(long timestamp) {
        MessagePassingQueue<ActivationEvent> activationEvents = this.activationEvents;

        for (ActivationEvent event = activationEvents.relaxedPeek(); event != null && event.happenedBefore(timestamp); event = activationEvents.relaxedPeek()) {
            activationEvents.relaxedPoll();
            event.handle(this);
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

    private static class ActivationEvent {
        private TraceContext traceContext;
        @Nullable
        private TraceContext previousContext;
        private long threadId;
        private boolean activation;
        private long timestamp;

        public ActivationEvent(TraceContext traceContext, long threadId, boolean activation, @Nullable TraceContext previousContext) {
            this.traceContext = traceContext;
            this.threadId = threadId;
            this.activation = activation;
            this.previousContext = previousContext;
            this.timestamp = System.nanoTime();
        }

        public boolean happenedBefore(long timestamp) {
            return this.timestamp < timestamp;
        }

        public static ActivationEvent activation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext) {
            return new ActivationEvent(context.getTraceContext().copy(), threadId, true, previousContext != null ? previousContext.getTraceContext() : null);
        }

        public static ActivationEvent deactivation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext) {
            return new ActivationEvent(context.getTraceContext().copy(), threadId, false, previousContext != null ? previousContext.getTraceContext() : null);
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
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    root.setActiveSpan(traceContext);
                }
            }
        }

        private void startProfiling(SamplingProfiler samplingProfiler) {
            ProfilingConfiguration config = samplingProfiler.config;
            CallTree.Root root = CallTree.createRoot(traceContext.getTraceContext().copy(), config.getSampleRate().getMillis(),
                config.getIncludedClasses(), config.getExcludedClasses());
            if (samplingProfiler.profiledThreads.put(threadId, root) != null) {
                logger.warn("Tried to register another profiling root on thread {} for span {}", threadId, traceContext);
            }
        }

        private void handleDeactivationEvent(SamplingProfiler samplingProfiler) {
            if (previousContext == null) {
                stopProfiling(samplingProfiler);
            } else {
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    root.setActiveSpan(previousContext);
                }
            }
        }

        private void stopProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root callTree = samplingProfiler.profiledThreads.get(threadId);
            if (callTree != null && traceContext.getTraceContext().equals(callTree.getTraceContext())) {
                samplingProfiler.profiledThreads.remove(threadId);
                callTree.end();
                callTree.removeNodesFasterThan(0.01f, 2);
                callTree.spanify();
            }
        }
    }
}
