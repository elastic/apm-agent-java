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

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.util.ExecutorUtils;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SamplingProfiler implements Runnable, LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfiler.class);
    private static final EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread> ACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContextHolder<?> active, TraceContextHolder<?> previouslyActive, Thread thread) {
                event.activation(active, thread.getId(), previouslyActive);
            }
        };
    private static final EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread> DEACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContextHolder active, TraceContextHolder previouslyActive, Thread thread) {
                event.deactivation(active, thread.getId(), previouslyActive);
            }
        };
    // sizeof(ActivationEvent) is 792B, so the ring buffer allocates around 1MiB
    static final int RING_BUFFER_SIZE = 1024;

    private final ProfilingConfiguration config;
    private final ScheduledExecutorService scheduler;
    private final Map<Long, CallTree.Root> profiledThreads;
    private final RingBuffer<ActivationEvent> eventBuffer;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private volatile boolean profilingSessionOngoing = false;
    private final StackFrameCache stackFrameCache = new StackFrameCache();
    private final Sequence sequence;
    private final SequenceBarrier sequenceBarrier;

    public SamplingProfiler(ElasticApmTracer tracer) {
        this(tracer,
            tracer.getConfig(ProfilingConfiguration.class),
            ExecutorUtils.createSingleThreadSchedulingDeamonPool("apm-sampling-profiler"),
            new HashMap<Long, CallTree.Root>());
    }

    SamplingProfiler(ElasticApmTracer tracer, ProfilingConfiguration config, ScheduledExecutorService scheduler, Map<Long, CallTree.Root> profiledThreads) {
        this.config = config;
        this.scheduler = scheduler;
        this.profiledThreads = profiledThreads;
        this.eventBuffer = createRingBuffer(tracer);
        this.sequence = new Sequence();
        // tells the ring buffer to not override slots which have not been read yet
        this.eventBuffer.addGatingSequences(sequence);
        // allows to get/wait for the sequences available for read via waitFor
        this.sequenceBarrier = eventBuffer.newBarrier();
    }

    private RingBuffer<ActivationEvent> createRingBuffer(final ElasticApmTracer tracer) {
        return RingBuffer.<ActivationEvent>createMultiProducer(
            new EventFactory<ActivationEvent>() {
                @Override
                public ActivationEvent newInstance() {
                    return new ActivationEvent(tracer);
                }
            },
            RING_BUFFER_SIZE,
            new NoWaitStrategy());
    }

    public boolean onActivation(Thread thread, TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        if (profilingSessionOngoing) {
            return eventBuffer.tryPublishEvent(ACTIVATION_EVENT_TRANSLATOR, activeSpan, previouslyActive, thread);
        }
        return false;
    }

    public boolean onDeactivation(Thread thread, TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        if (profilingSessionOngoing) {
            return eventBuffer.tryPublishEvent(DEACTIVATION_EVENT_TRANSLATOR, activeSpan, previouslyActive, thread);
        }
        return false;
    }

    @Override
    public void run() {
        TimeDuration sampleRate = config.getSampleRate();
        TimeDuration profilingDuration = config.getProfilingDuration();
        TimeDuration profilingInterval = config.getProfilingInterval();
        profilingSessionOngoing = true;

        logger.debug("Start profiling session");
        profile(sampleRate, profilingDuration);
        logger.debug("End profiling session");

        if (profilingInterval.getMillis() != 0) {
            profilingSessionOngoing = false;
            // TODO do we want to create inferred spans for partially profiled transactions?
            profiledThreads.clear();
        }
        // clears the interrupted status so that the thread can return to the pool
        if (!Thread.interrupted()) {
            long delay = profilingInterval.getMillis() - config.getProfilingDuration().getMillis();
            scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void profile(TimeDuration sampleRate, TimeDuration profilingDuration) {
        long sampleRateMs = sampleRate.getMillis();
        long deadline = profilingDuration.getMillis() + System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() < deadline) {
            try {
                long startNs = System.nanoTime();

                takeThreadSnapshot();

                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                logger.trace("Taking snapshot took {}ms", durationMs);

                Thread.sleep(Math.max(sampleRateMs - durationMs, 0));
            } catch (RuntimeException e) {
                logger.error("Exception while taking profiling snapshot", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void takeThreadSnapshot() {
        List<WildcardMatcher> excludedClasses = config.getExcludedClasses();
        List<WildcardMatcher> includedClasses = config.getIncludedClasses();
        long nanoTime = System.nanoTime();
        processActivationEventsUpTo(nanoTime);
        long[] profiledThreadIds = getProfiledThreadIds();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(profiledThreadIds, Integer.MAX_VALUE);
        if (profiledThreadIds.length == 0) {
            return;
        }
        logger.trace("Taking snapshot of threads {} timestamp {}", profiledThreadIds, nanoTime);

        List<StackFrame> stackTraces = new ArrayList<>(256);
        for (int i = 0; i < profiledThreadIds.length; i++) {
            long threadId = profiledThreadIds[i];
            ThreadInfo threadInfo = threadInfos[i];
            if (threadInfo != null) {
                CallTree.Root root = profiledThreads.get(threadId);
                for (StackTraceElement stackTraceElement : ThreadInfoStacktraceAccessor.getStackTrace(threadInfo)) {
                    if (isIncluded(stackTraceElement, excludedClasses, includedClasses)) {
                        stackTraces.add(stackFrameCache.getStackFrame(stackTraceElement.getClassName(), stackTraceElement.getMethodName()));
                    }
                }
                root.addStackTrace(stackTraces, nanoTime);
                stackTraces.clear();
            }
        }
    }

    private boolean isIncluded(StackTraceElement stackTraceElement, List<WildcardMatcher> excludedClasses, List<WildcardMatcher> includedClasses) {
        return WildcardMatcher.isAnyMatch(includedClasses, stackTraceElement.getClassName()) && WildcardMatcher.isNoneMatch(excludedClasses, stackTraceElement.getClassName());
    }

    public void processActivationEventsUpTo(long timestamp) {
        RingBuffer<ActivationEvent> eventBuffer = this.eventBuffer;
        try {
            long nextSequence = sequence.get() + 1L;
            // We never want to wait until new elements are available,
            // we just want to process all available events
            // See NoWaitStrategy
            long availableSequence = sequenceBarrier.waitFor(nextSequence);
            while (nextSequence <= availableSequence) {
                ActivationEvent event = eventBuffer.get(nextSequence);
                if (event.happenedBefore(timestamp)) {
                    event.handle(this);
                    nextSequence++;
                } else {
                    // the next events are guaranteed to be older
                    // only after a sequence is acquired, the timestamp is set within the EventTranslator
                    sequence.set(nextSequence - 1);
                    return;
                }
            }
            sequence.set(availableSequence);
        } catch (Exception ignore) {
            // our NoWaitStrategy does not throw exceptions
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
        if (config.isProfilingEnabled()) {
            scheduler.submit(this);
        }
    }

    @Override
    public void stop() throws Exception {
        // cancels/interrupts the profiling thread
        scheduler.shutdownNow();
    }

    // for testing
    CallTree.Root getRoot() {
        return profiledThreads.get(Thread.currentThread().getId());
    }

    void setProfilingSessionOngoing(boolean profilingSessionOngoing) {
        this.profilingSessionOngoing = profilingSessionOngoing;
    }

    void clear() {
        profiledThreads.clear();
    }
    // --

    private static class ActivationEvent {
        private TraceContext traceContext;
        private TraceContext previousContext;
        private long threadId;
        private boolean activation;
        private long timestamp;

        public ActivationEvent(ElasticApmTracer tracer) {
            traceContext = TraceContext.with64BitId(tracer);
            previousContext = TraceContext.with64BitId(tracer);
        }

        public boolean happenedBefore(long timestamp) {
            return this.timestamp < timestamp;
        }

        public void activation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext) {
            set(context.getTraceContext(), threadId, true, previousContext != null ? previousContext.getTraceContext() : null);
        }

        public void deactivation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext) {
            set(context.getTraceContext(), threadId, false, previousContext != null ? previousContext.getTraceContext() : null);
        }

        private void set(TraceContext traceContext, long threadId, boolean activation, @Nullable TraceContext previousContext) {
            this.traceContext.copyFrom(traceContext);
            this.threadId = threadId;
            this.activation = activation;
            if (previousContext != null) {
                this.previousContext.copyFrom(previousContext);
            } else {
                this.previousContext.resetState();
            }
            this.timestamp = System.nanoTime();
        }

        public void handle(SamplingProfiler samplingProfiler) {
            if (activation) {
                handleActivationEvent(samplingProfiler);
            } else {
                handleDeactivationEvent(samplingProfiler);
            }
        }

        private void handleActivationEvent(SamplingProfiler samplingProfiler) {
            if (previousContext.getTraceId().isEmpty()) {
                startProfiling(samplingProfiler);
            } else {
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    root.setActiveSpan(traceContext.copy());
                }
            }
        }

        private void startProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root root = CallTree.createRoot(traceContext.copy(), timestamp);
            samplingProfiler.profiledThreads.put(threadId, root);
        }

        private void handleDeactivationEvent(SamplingProfiler samplingProfiler) {
            if (previousContext.getTraceId().isEmpty()) {
                stopProfiling(samplingProfiler);
            } else {
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    root.setActiveSpan(previousContext.copy());
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

    /**
     * Does not wait but immediately returns the highest sequence which is available for read
     * We never want to wait until new elements are available,
     * we just want to process all available events
     */
    private static class NoWaitStrategy implements WaitStrategy {

        @Override
        public long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier) {
            return dependentSequence.get();
        }

        @Override
        public void signalAllWhenBlocking() {
        }
    }
}
