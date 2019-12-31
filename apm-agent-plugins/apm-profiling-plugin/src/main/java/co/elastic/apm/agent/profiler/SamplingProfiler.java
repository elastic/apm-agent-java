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
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.ListBasedObjectPool;
import co.elastic.apm.agent.profiler.asyncprofiler.AsyncProfiler;
import co.elastic.apm.agent.profiler.asyncprofiler.JfrParser;
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SamplingProfiler implements Runnable, LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfiler.class);
    private final EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread> ACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContextHolder<?> active, TraceContextHolder<?> previouslyActive, Thread thread) {
                event.activation(active, threadMapper.getNativeThreadId(thread), previouslyActive, nanoClock.nanoTime());
            }
        };
    private final EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread> DEACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorThreeArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>, Thread>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContextHolder active, TraceContextHolder previouslyActive, Thread thread) {
                event.deactivation(active, threadMapper.getNativeThreadId(thread), previouslyActive, nanoClock.nanoTime());
            }
        };
    // sizeof(ActivationEvent) is 176B so the ring buffer should be around 880KiB
    static final int RING_BUFFER_SIZE = 4 * 1024;

    private final ProfilingConfiguration config;
    private final ScheduledExecutorService scheduler;
    private final Map<Long, CallTree.Root> profiledThreads;
    private final RingBuffer<ActivationEvent> eventBuffer;
    private volatile boolean profilingSessionOngoing = false;
    private final Sequence sequence;
    private final SequenceBarrier sequenceBarrier;
    private final ElasticApmTracer tracer;
    private final NanoClock nanoClock;
    private final ObjectPool<CallTree.Root> rootPool;
    private final AsyncProfiler asyncProfiler = AsyncProfiler.getInstance();
    private final NativeThreadIdToJavaThreadMapper threadMapper = new NativeThreadIdToJavaThreadMapper();

    public SamplingProfiler(ElasticApmTracer tracer, NanoClock nanoClock) {
        this(tracer,
            tracer.getConfig(ProfilingConfiguration.class),
            ExecutorUtils.createSingleThreadSchedulingDeamonPool("apm-sampling-profiler"),
            new HashMap<Long, CallTree.Root>(), nanoClock);
    }

    SamplingProfiler(final ElasticApmTracer tracer, ProfilingConfiguration config, ScheduledExecutorService scheduler, Map<Long, CallTree.Root> profiledThreads, NanoClock nanoClock) {
        this.tracer = tracer;
        this.config = config;
        this.scheduler = scheduler;
        this.profiledThreads = profiledThreads;
        this.nanoClock = nanoClock;
        this.eventBuffer = createRingBuffer();
        this.sequence = new Sequence();
        // tells the ring buffer to not override slots which have not been read yet
        this.eventBuffer.addGatingSequences(sequence);
        // allows to get/wait for the sequences available for read via waitFor
        this.sequenceBarrier = eventBuffer.newBarrier();
        this.rootPool = ListBasedObjectPool.<CallTree.Root>ofRecyclable(new ArrayList<CallTree.Root>(), 512, new Allocator<CallTree.Root>() {
            @Override
            public CallTree.Root createInstance() {
                return new CallTree.Root(tracer);
            }
        });
    }

    private RingBuffer<ActivationEvent> createRingBuffer() {
        return RingBuffer.<ActivationEvent>createMultiProducer(
            new EventFactory<ActivationEvent>() {
                @Override
                public ActivationEvent newInstance() {
                    return new ActivationEvent();
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
        if (config.isProfilingDisabled()) {
            scheduler.schedule(this, config.getProfilingInterval().getMillis(), TimeUnit.MILLISECONDS);
            return;
        }

        TimeDuration sampleRate = config.getSampleRate();
        TimeDuration profilingDuration = config.getProfilingDuration();

        setProfilingSessionOngoing(true);

        logger.debug("Start profiling session");
        try {
            profile(sampleRate, profilingDuration);
        } catch (Exception e) {
            logger.error("Stopping profiler", e);
            return;
        }
        logger.debug("End profiling session");

        boolean interrupted = Thread.currentThread().isInterrupted();
        boolean continueProfilingSession = config.isNonStopProfiling() && !interrupted && config.isProfilingEnabled();
        setProfilingSessionOngoing(continueProfilingSession);

        if (!interrupted) {
            long delay = config.getProfilingInterval().getMillis() - profilingDuration.getMillis();
            scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void profile(TimeDuration sampleRate, TimeDuration profilingDuration) throws Exception {
        File file = new File(System.getProperty("java.io.tmpdir") + "/traces" + System.currentTimeMillis() + ".jfr");
        try {
            String startMessage = asyncProfiler.execute("start,jfr,event=wall,interval=" + sampleRate.getMillis() + "ms,alluser,file=" + file);
            logger.info(startMessage);

            Thread.sleep(profilingDuration.getMillis());

            String stopMessage = asyncProfiler.execute("stop");
            logger.info(stopMessage);

            processTraces(file);
        } catch (InterruptedException e) {
            asyncProfiler.stop();
            Thread.currentThread().interrupt();
        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    private void processTraces(File file) throws IOException {
        List<WildcardMatcher> excludedClasses = config.getExcludedClasses();
        List<WildcardMatcher> includedClasses = config.getIncludedClasses();
        JfrParser jfrParser = new JfrParser(file, excludedClasses, includedClasses);
        final SortedSet<StackTraceEvent> stackTraceEvents = getStackTraceEvents(jfrParser);
        logger.debug("Processing {} stack traces", stackTraceEvents.size());
        List<StackFrame> stackFrames = new ArrayList<>();
        ElasticApmTracer tracer = this.tracer;
        for (Iterator<StackTraceEvent> iterator = stackTraceEvents.iterator(); iterator.hasNext(); ) {
            StackTraceEvent stackTrace = iterator.next();
            // make StackTraceEvent GC eligible asap
            iterator.remove();

            // TODO write activation events into MappedByteBuffer (memory-mapped file)
            processActivationEventsUpTo(stackTrace.nanoTime);
            CallTree.Root root = profiledThreads.get((long) stackTrace.threadId);
            if (root != null) {
                jfrParser.getStackTrace(stackTrace.stackTraceId, true, stackFrames);
                root.addStackTrace(tracer, stackFrames, stackTrace.nanoTime);
            }
            stackFrames.clear();
        }
    }

    /**
     * Returns stack trace events of relevant threads sorted by timestamp.
     * The events in the JFR file are not in order.
     * Even for the same thread, a more recent event might come before an older event.
     * In order to be able to correlate stack trace events and activation events, both need to be in order.
     *
     * Returns only events for threads where at least one activation happened
     */
    private SortedSet<StackTraceEvent> getStackTraceEvents(JfrParser jfrParser) throws IOException {
        final long[] nativeThreadIds = threadMapper.getSortedNativeThreadIds();
        final SortedSet<StackTraceEvent> stackTraceEvents = new TreeSet<>();
        jfrParser.consumeStackTraces(new JfrParser.StackTraceConsumer() {
            @Override
            public void onCallTree(int threadId, long stackTraceId, long nanoTime) {
                // binary search in primitive array as opposed to lookup in set to avoid allocations
                // this is beneficial as number of stackTraces is much greater than number of threads
                if (Arrays.binarySearch(nativeThreadIds, threadId) >= 0) {
                    stackTraceEvents.add(new StackTraceEvent(nanoTime, stackTraceId, threadId));
                }
            }
        });
        return stackTraceEvents;
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
                if (event.happenedBeforeOrAt(timestamp)) {
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
        for (Map.Entry<Long, CallTree.Root> entry : profiledThreads.entrySet()) {
            if (entry.getValue() != null) {
                result[i++] = entry.getKey();
            }
        }
        if (i < result.length) {
            long[] resultTruncated = new long[i];
            System.arraycopy(result, 0, resultTruncated, 0, i);
            return resultTruncated;
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

    void setProfilingSessionOngoing(boolean profilingSessionOngoing) {
        this.profilingSessionOngoing = profilingSessionOngoing;
        if (!profilingSessionOngoing) {
            profiledThreads.clear();
        }
    }

    // for testing
    CallTree.Root getRoot() {
        return profiledThreads.get(Thread.currentThread().getId());
    }

    void clear() {
        profiledThreads.clear();
        try {
            // skip all events in buffer
            sequence.set(sequenceBarrier.waitFor(sequence.get() + 1L));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    // --

    private static class StackTraceEvent implements Comparable<StackTraceEvent> {
        private final long nanoTime;
        private final long stackTraceId;
        private final int threadId;

        private StackTraceEvent(long nanoTime, long stackTraceId, int threadId) {
            this.nanoTime = nanoTime;
            this.stackTraceId = stackTraceId;
            this.threadId = threadId;
        }

        @Override
        public int compareTo(StackTraceEvent o) {
            return Long.compare(nanoTime, o.nanoTime);
        }
    }

    private static class ActivationEvent {
        @Nullable
        private String serviceName;
        private byte[] traceContextBuffer = new byte[TraceContext.SERIALIZED_LENGTH];
        private byte[] previousContextBuffer = new byte[TraceContext.SERIALIZED_LENGTH];
        private boolean rootContext;
        private long threadId;
        private boolean activation;
        private long timestamp;

        public boolean happenedBeforeOrAt(long timestamp) {
            return this.timestamp <= timestamp;
        }

        public void activation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext, long nanoTime) {
            set(context.getTraceContext(), threadId, true, previousContext != null ? previousContext.getTraceContext() : null, nanoTime);
        }

        public void deactivation(TraceContextHolder<?> context, long threadId, @Nullable TraceContextHolder<?> previousContext, long nanoTime) {
            set(context.getTraceContext(), threadId, false, previousContext != null ? previousContext.getTraceContext() : null, nanoTime);
        }

        private void set(TraceContext traceContext, long threadId, boolean activation, @Nullable TraceContext previousContext, long nanoTime) {
            traceContext.serialize(traceContextBuffer);
            this.threadId = threadId;
            this.activation = activation;
            this.serviceName = traceContext.getServiceName();
            if (previousContext != null) {
                previousContext.serialize(previousContextBuffer);
                rootContext = false;
            } else {
                rootContext = true;
            }
            this.timestamp = nanoTime;
        }

        public void handle(SamplingProfiler samplingProfiler) {
            if (activation) {
                handleActivationEvent(samplingProfiler);
            } else {
                handleDeactivationEvent(samplingProfiler);
            }
        }

        private void handleActivationEvent(SamplingProfiler samplingProfiler) {
            if (rootContext) {
                startProfiling(samplingProfiler);
            } else {
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    root.onActivation(traceContextBuffer, timestamp);
                }
            }
        }

        private void startProfiling(SamplingProfiler samplingProfiler) {
            logger.debug("Start profiling for thread {}", threadId);
            CallTree.Root root = CallTree.createRoot(samplingProfiler.rootPool, traceContextBuffer, serviceName, timestamp);
            samplingProfiler.profiledThreads.put(threadId, root);
        }

        private TraceContext getTraceContext(SamplingProfiler samplingProfiler, byte[] traceContextBuffer) {
            TraceContext traceContext = TraceContext.with64BitId(samplingProfiler.tracer);
            traceContext.deserialize(traceContextBuffer, serviceName);
            return traceContext;
        }

        private void handleDeactivationEvent(SamplingProfiler samplingProfiler) {
            if (rootContext) {
                stopProfiling(samplingProfiler);
            } else {
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    root.onDeactivation(previousContextBuffer, timestamp);
                }
            }
        }

        private void stopProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root callTree = samplingProfiler.profiledThreads.get(threadId);
            if (callTree != null && callTree.getTraceContext().traceIdAndIdEquals(traceContextBuffer)) {
                // not removing so Map.Entry objects are reused
                samplingProfiler.profiledThreads.put(threadId, null);
                callTree.end();
                callTree.removeNodesFasterThan(0.01f, 2);
                callTree.spanify();
                samplingProfiler.rootPool.recycle(callTree);
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
