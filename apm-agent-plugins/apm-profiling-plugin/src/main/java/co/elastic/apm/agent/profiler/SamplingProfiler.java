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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.ListBasedObjectPool;
import co.elastic.apm.agent.profiler.asyncprofiler.AsyncProfiler;
import co.elastic.apm.agent.profiler.asyncprofiler.JfrParser;
import co.elastic.apm.agent.profiler.collections.Long2ObjectHashMap;
import co.elastic.apm.agent.profiler.collections.LongHashSet;
import co.elastic.apm.agent.util.ExecutorUtils;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Correlates {@link ActivationEvent}s with {@link StackFrame}s which are recorded by {@link AsyncProfiler},
 * a native <a href="http://psy-lob-saw.blogspot.com/2016/06/the-pros-and-cons-of-agct.html">{@code AsyncGetCallTree}</a>-based
 * (and therefore <a href="http://psy-lob-saw.blogspot.com/2016/02/why-most-sampling-java-profilers-are.html"> non safepoint-biased</a>)
 * JVMTI agent.
 * <p>
 * Recording of {@link ActivationEvent}s:
 * </p>
 * <p>
 * The {@link #onActivation} and {@link #onDeactivation} methods are called by {@link ProfilingActivationListener}
 * which register an {@link ActivationEvent} in to a {@linkplain #eventBuffer ring buffer} whenever a {@link Span}
 * gets {@link Span#activate()}d or {@link Span#deactivate()}d while a {@linkplain #profilingSessionOngoing profiling session is ongoing}.
 * A background thread consumes the {@link ActivationEvent}s and writes them to a {@linkplain #activationEventBuffer memory-mapped file}.
 * That is necessary because within a profiling session (which lasts 10s by default) there may be many more {@link ActivationEvent}s
 * than the ring buffer can hold {@link #RING_BUFFER_SIZE}.
 * The file can hold {@link #ACTIVATION_EVENTS_IN_FILE} events and each is {@link ActivationEvent#SERIALIZED_SIZE} in size.
 * This process is completely garbage free thanks to the {@link RingBuffer} acting as an object pool for {@link ActivationEvent}s.
 * </p>
 * <p>
 * Recording stack traces:
 * </p>
 * <p>
 * The same background thread that processes the {@link ActivationEvent}s starts the wall clock profiler of async-profiler via
 * {@link AsyncProfiler#execute(String)}.
 * After the {@link ProfilingConfiguration#getProfilingDuration()} is over it stops the profiling and starts processing the JFR file created
 * by async-profiler with {@link JfrParser}.
 * </p>
 * <p>
 * Correlating {@link ActivationEvent}s with the traces recorded by {@link AsyncProfiler}:
 * </p>
 * <p>
 * After both the JFR file and the memory-mapped file containing the {@link ActivationEvent}s have been written,
 * it's now time to process them in tandem by correlating based on thread ids and timestamps.
 * The result of this correlation, performed by {@link #processTraces(File)},
 * are {@link CallTree}s which are created for each thread which has seen an {@linkplain Span#activate() activation}
 * and at least one stack trace.
 * Once {@linkplain ActivationEvent#handleDeactivationEvent(SamplingProfiler) handling the deactivation event} of the root span in a thread
 * (after which {@link ElasticApmTracer#getActive()} would return {@code null}),
 * the {@link CallTree} is {@linkplain CallTree#spanify(CallTree.Root, TraceContext) converted into regular spans}.
 * </p>
 * <p>
 * Overall, the allocation rate does not depend on the number of {@link ActivationEvent}s but only on
 * {@link ProfilingConfiguration#getProfilingInterval()} and {@link ProfilingConfiguration#getSamplingInterval()}.
 * Having said that, there are some optimizations so that the JFR file is not processed at all if there have not been any
 * {@link ActivationEvent} in a given profiling session.
 * Also, only if there's a {@link CallTree.Root} for a {@link StackTraceEvent},
 * we will {@link JfrParser#resolveStackTrace(long, boolean, List, int) resolve the full stack trace}.
 * </p>
 */
public class SamplingProfiler implements Runnable, LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfiler.class);
    private static final int ACTIVATION_EVENTS_IN_FILE = 1_000_000;
    private static final int MAX_STACK_DEPTH = 256;
    private static final int PRE_ALLOCATE_ACTIVATION_EVENTS_FILE_MB = 10;
    private final EventTranslatorTwoArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>> ACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorTwoArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContextHolder<?> active, TraceContextHolder<?> previouslyActive) {
                event.activation(active, threadMapper.getNativeThreadId(), previouslyActive, nanoClock.nanoTime());
            }
        };
    private final EventTranslatorTwoArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>> DEACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorTwoArg<ActivationEvent, TraceContextHolder<?>, TraceContextHolder<?>>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContextHolder active, TraceContextHolder previouslyActive) {
                event.deactivation(active, threadMapper.getNativeThreadId(), previouslyActive, nanoClock.nanoTime());
            }
        };
    // sizeof(ActivationEvent) is 176B so the ring buffer should be around 880KiB
    static final int RING_BUFFER_SIZE = 4 * 1024;

    private final ProfilingConfiguration config;
    private final ScheduledExecutorService scheduler;
    private final Long2ObjectHashMap<CallTree.Root> profiledThreads = new Long2ObjectHashMap<>();
    private final RingBuffer<ActivationEvent> eventBuffer;
    private volatile boolean profilingSessionOngoing = false;
    private final Sequence sequence;
    private final ElasticApmTracer tracer;
    private final NanoClock nanoClock;
    private final ObjectPool<CallTree.Root> rootPool;
    private final NativeThreadIdToJavaThreadMapper threadMapper = new NativeThreadIdToJavaThreadMapper();
    private final MappedByteBuffer activationEventBuffer;
    private final EventPoller<ActivationEvent> poller;
    private final File activationEventsFile;
    private final File jfrFile;
    private final WriteActivationEventToFileHandler writeActivationEventToFileHandler = new WriteActivationEventToFileHandler();
    private final JfrParser jfrParser = new JfrParser();
    private volatile int profilingSessions;

    public SamplingProfiler(ElasticApmTracer tracer, NanoClock nanoClock) throws IOException {
        this(tracer,
            tracer.getConfig(ProfilingConfiguration.class),
            ExecutorUtils.createSingleThreadSchedulingDeamonPool("sampling-profiler"),
            nanoClock);
    }

    SamplingProfiler(final ElasticApmTracer tracer, ProfilingConfiguration config, ScheduledExecutorService scheduler, NanoClock nanoClock) throws IOException {
        this.tracer = tracer;
        this.config = config;
        this.scheduler = scheduler;
        this.nanoClock = nanoClock;
        this.eventBuffer = createRingBuffer();
        this.sequence = new Sequence();
        // tells the ring buffer to not override slots which have not been read yet
        this.eventBuffer.addGatingSequences(sequence);
        this.poller = eventBuffer.newPoller();
        // call tree roots are pooled so that fast activations/deactivations with no associated stack traces don't cause allocations
        this.rootPool = ListBasedObjectPool.<CallTree.Root>ofRecyclable(new ArrayList<CallTree.Root>(), 512, new Allocator<CallTree.Root>() {
            @Override
            public CallTree.Root createInstance() {
                return new CallTree.Root(tracer);
            }
        });
        jfrFile = File.createTempFile("apm-traces-", ".jfr");
        activationEventsFile = File.createTempFile("apm-activation-events-", ".bin");
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(activationEventsFile, "rw")) {
            activationEventBuffer = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, ACTIVATION_EVENTS_IN_FILE * ActivationEvent.SERIALIZED_SIZE);
            preAllocate(activationEventBuffer, PRE_ALLOCATE_ACTIVATION_EVENTS_FILE_MB);
        }
    }

    /**
     * Makes sure that the first blocks of the file are contiguous to provide fast sequential access
     */
    private static void preAllocate(MappedByteBuffer activationEventBuffer, int mb) {
        byte[] oneKb = new byte[1024];
        for (int i = 0; i < mb * 1024; i++) {
            activationEventBuffer.put(oneKb);
        }
        activationEventBuffer.clear();
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

    /**
     * Called whenever a span is activated.
     * <p>
     * This and {@link #onDeactivation(TraceContextHolder, TraceContextHolder)} are the only methods which are executed in a multi-threaded
     * context.
     * </p>
     *
     * @param activeSpan       the span which is about to be activated
     * @param previouslyActive the span which has previously been activated
     * @return {@code true}, if the event could be processed, {@code false} if the internal event queue is full which means the event has been discarded
     */
    public boolean onActivation(TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        if (profilingSessionOngoing) {
            boolean success = eventBuffer.tryPublishEvent(ACTIVATION_EVENT_TRANSLATOR, activeSpan, previouslyActive);
            if (!success && logger.isDebugEnabled()) {
                logger.debug("Could not add activation event to ring buffer as no slots are available");
            }
            return success;
        }
        return false;
    }

    /**
     * Called whenever a span is deactivated.
     * <p>
     * This and {@link #onActivation(TraceContextHolder, TraceContextHolder)} are the only methods which are executed in a multi-threaded
     * context.
     * </p>
     *
     * @param activeSpan       the span which is about to be activated
     * @param previouslyActive the span which has previously been activated
     * @return {@code true}, if the event could be processed, {@code false} if the internal event queue is full which means the event has been discarded
     */
    public boolean onDeactivation(TraceContextHolder<?> activeSpan, @Nullable TraceContextHolder<?> previouslyActive) {
        if (profilingSessionOngoing) {
            boolean success = eventBuffer.tryPublishEvent(DEACTIVATION_EVENT_TRANSLATOR, activeSpan, previouslyActive);
            if (!success && logger.isDebugEnabled()) {
                logger.debug("Could not add deactivation event to ring buffer as no slots are available");
            }
            return success;
        }
        return false;
    }

    @Override
    public void run() {
        profilingSessions++;
        if (config.isProfilingDisabled()) {
            scheduler.schedule(this, config.getProfilingInterval().getMillis(), TimeUnit.MILLISECONDS);
            return;
        }

        TimeDuration sampleRate = config.getSamplingInterval();
        TimeDuration profilingDuration = config.getProfilingDuration();

        setProfilingSessionOngoing(true);

        logger.debug("Start profiling session");
        try {
            profile(sampleRate, profilingDuration);
        } catch (Throwable t) {
            setProfilingSessionOngoing(false);
            logger.error("Stopping profiler", t);
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
        AsyncProfiler asyncProfiler = AsyncProfiler.getInstance();
        try {
            String startMessage = asyncProfiler.execute("start,jfr,event=wall,interval=" + sampleRate.getMillis() + "ms,alluser,file=" + jfrFile);
            logger.debug(startMessage);

            consumeActivationEventsFromRingBufferAndWriteToFile(profilingDuration);

            String stopMessage = asyncProfiler.execute("stop");
            logger.debug(stopMessage);

            processTraces(jfrFile);
        } catch (InterruptedException e) {
            asyncProfiler.stop();
            Thread.currentThread().interrupt();
        }
    }

    private void consumeActivationEventsFromRingBufferAndWriteToFile(TimeDuration profilingDuration) throws Exception {
        resetActivationEventBuffer();
        long threshold = System.currentTimeMillis() + profilingDuration.getMillis();
        long initialSleep = 100_000;
        long maxSleep = 10_000_000;
        long sleep = initialSleep;
        while (System.currentTimeMillis() < threshold) {
            if (activationEventBuffer.hasRemaining()) {
                EventPoller.PollState poll = consumeActivationEventsFromRingBufferAndWriteToFile();
                if (poll == EventPoller.PollState.PROCESSING) {
                    sleep = initialSleep;
                } else {
                    if (sleep < maxSleep) {
                        sleep *= 2;
                    }
                }
                LockSupport.parkNanos(sleep);
            } else {
                logger.warn("The activation events file is full. Try lowering the profiling_duration.");
                // the file is full, sleep the rest of the profilingDuration
                Thread.sleep(Math.max(0, threshold - System.currentTimeMillis()));
            }
        }
    }

    private void resetActivationEventBuffer() {
        ((Buffer) activationEventBuffer).clear();
    }

    EventPoller.PollState consumeActivationEventsFromRingBufferAndWriteToFile() throws Exception {
        return poller.poll(writeActivationEventToFileHandler);
    }

    private void processTraces(File file) throws IOException {
        if (activationEventBuffer.position() == 0) {
            logger.debug("No activation events during this period. Skip processing stack traces.");
            return;
        }
        long start = System.nanoTime();
        List<WildcardMatcher> excludedClasses = config.getExcludedClasses();
        List<WildcardMatcher> includedClasses = config.getIncludedClasses();
        try {
            jfrParser.parse(file, excludedClasses, includedClasses);
            startProcessingActivationEventsFile();
            final SortedSet<StackTraceEvent> stackTraceEvents = getStackTraceEvents(jfrParser);
            if (logger.isDebugEnabled()) {
                logger.debug("Processing {} stack traces", stackTraceEvents.size());
            }
            List<StackFrame> stackFrames = new ArrayList<>();
            ElasticApmTracer tracer = this.tracer;
            ActivationEvent event = new ActivationEvent();
            for (StackTraceEvent stackTrace : stackTraceEvents) {
                processActivationEventsUpTo(stackTrace.nanoTime, event);
                CallTree.Root root = profiledThreads.get(stackTrace.threadId);
                if (root != null) {
                    jfrParser.resolveStackTrace(stackTrace.stackTraceId, true, stackFrames, MAX_STACK_DEPTH);
                    if (stackFrames.size() == MAX_STACK_DEPTH) {
                        logger.debug("Max stack depth reached. Set profiling_included_classes or profiling_excluded_classes.");
                    }
                    root.addStackTrace(tracer, stackFrames, stackTrace.nanoTime);
                }
                stackFrames.clear();
            }
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing traces took {}µs", (System.nanoTime() - start) / 1000);
            }
            jfrParser.resetState();
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
        final LongHashSet nativeThreadIds = threadMapper.getNativeThreadIds();
        final SortedSet<StackTraceEvent> stackTraceEvents = new TreeSet<>();
        jfrParser.consumeStackTraces(new JfrParser.StackTraceConsumer() {
            @Override
            public void onCallTree(int threadId, long stackTraceId, long nanoTime) {
                if (nativeThreadIds.contains(threadId)) {
                    stackTraceEvents.add(new StackTraceEvent(nanoTime, stackTraceId, threadId));
                }
            }
        });
        return stackTraceEvents;
    }

    void processActivationEventsUpTo(long timestamp) {
        processActivationEventsUpTo(timestamp, new ActivationEvent());
    }

    public void processActivationEventsUpTo(long timestamp, ActivationEvent event) {
        MappedByteBuffer buf = this.activationEventBuffer;
        while (buf.hasRemaining()) {
            long eventTimestamp = peekLong(buf);
            if (eventTimestamp <= timestamp) {
                event.deserialize(buf);
                event.handle(this);
            } else {
                return;
            }
        }
    }

    private static long peekLong(ByteBuffer buf) {
        int pos = buf.position();
        try {
            return buf.getLong();
        } finally {
            ((Buffer) buf).position(pos);
        }
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        scheduler.submit(this);
    }

    @Override
    public void stop() throws Exception {
        // cancels/interrupts the profiling thread
        scheduler.shutdownNow();
        if (!jfrFile.delete()) {
            jfrFile.deleteOnExit();
        }
        if (!activationEventsFile.delete()) {
            activationEventsFile.deleteOnExit();
        }
    }

    void setProfilingSessionOngoing(boolean profilingSessionOngoing) {
        this.profilingSessionOngoing = profilingSessionOngoing;
        if (!profilingSessionOngoing) {
            profiledThreads.clear();
        }
    }

    void startProcessingActivationEventsFile() {
        ((Buffer) activationEventBuffer).flip();
    }

    // for testing
    CallTree.Root getRoot() {
        return profiledThreads.get(threadMapper.getNativeThreadId());
    }

    void clear() {
        profiledThreads.clear();
        // consume all remaining events from the ring buffer
        try {
            poller.poll(new EventPoller.Handler<ActivationEvent>() {
                @Override
                public boolean onEvent(ActivationEvent event, long sequence, boolean endOfBatch) {
                    SamplingProfiler.this.sequence.set(sequence);
                    return true;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        resetActivationEventBuffer();
    }

    int getProfilingSessions() {
        return profilingSessions;
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
        public static final int SERIALIZED_SIZE =
            Long.SIZE / Byte.SIZE + // timestamp
                Short.SIZE / Byte.SIZE + // serviceName index
                TraceContext.SERIALIZED_LENGTH + // traceContextBuffer
                TraceContext.SERIALIZED_LENGTH + // previousContextBuffer
                1 + // rootContext
                Long.SIZE / Byte.SIZE + // threadId
                1; // activation

        private static final Map<String, Short> serviceNameMap = new HashMap<>();
        private static final Map<Short, String> serviceNameBackMap = new HashMap<>();

        private long timestamp;
        @Nullable
        private String serviceName;
        private byte[] traceContextBuffer = new byte[TraceContext.SERIALIZED_LENGTH];
        private byte[] previousContextBuffer = new byte[TraceContext.SERIALIZED_LENGTH];
        private boolean rootContext;
        private long threadId;
        private boolean activation;

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
            if (logger.isDebugEnabled()) {
                logger.debug("Start profiling for thread {}", threadId);
            }
            CallTree.Root root = CallTree.createRoot(samplingProfiler.rootPool, traceContextBuffer, serviceName, timestamp);
            samplingProfiler.profiledThreads.put(threadId, root);
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
            if (callTree != null && callTree.getRootContext().traceIdAndIdEquals(traceContextBuffer)) {
                samplingProfiler.profiledThreads.remove(threadId);
                callTree.end();
                callTree.removeNodesFasterThan(0.01f, 2);
                callTree.spanify();
                samplingProfiler.rootPool.recycle(callTree);
            }
        }

        public void serialize(ByteBuffer buf) {
            buf.putLong(timestamp);
            buf.putShort(getServiceNameIndex());
            buf.put(traceContextBuffer);
            buf.put(previousContextBuffer);
            buf.put(rootContext ? (byte) 1 : (byte) 0);
            buf.putLong(threadId);
            buf.put(activation ? (byte) 1 : (byte) 0);
        }

        public void deserialize(ByteBuffer buf) {
            timestamp = buf.getLong();
            serviceName = serviceNameBackMap.get(buf.getShort());
            buf.get(traceContextBuffer);
            buf.get(previousContextBuffer);
            rootContext = buf.get() == 1;
            threadId = buf.getLong();
            activation = buf.get() == 1;
        }

        private short getServiceNameIndex() {
            Short index = serviceNameMap.get(serviceName);
            if (index == null) {
                index = (short) serviceNameMap.size();
                serviceNameMap.put(serviceName, index);
                serviceNameBackMap.put(index, serviceName);
            }
            return index;
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

    // extracting to a class instead of instantiating an anonymous inner class makes a huge difference in allocations
    private class WriteActivationEventToFileHandler implements EventPoller.Handler<ActivationEvent> {
        @Override
        public boolean onEvent(ActivationEvent event, long sequence, boolean endOfBatch) {
            if (endOfBatch) {
                SamplingProfiler.this.sequence.set(sequence);
            }
            if (activationEventBuffer.hasRemaining()) {
                event.serialize(activationEventBuffer);
                return true;
            }
            return false;
        }
    }
}
