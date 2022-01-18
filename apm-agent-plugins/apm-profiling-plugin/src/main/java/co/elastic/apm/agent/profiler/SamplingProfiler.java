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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.ListBasedObjectPool;
import co.elastic.apm.agent.profiler.asyncprofiler.AsyncProfiler;
import co.elastic.apm.agent.profiler.asyncprofiler.JfrParser;
import co.elastic.apm.agent.profiler.collections.Long2ObjectHashMap;
import co.elastic.apm.agent.util.ExecutorUtils;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

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
 * which register an {@link ActivationEvent} to a {@linkplain #eventBuffer ring buffer} whenever a {@link Span}
 * gets {@link Span#activate()}d or {@link Span#deactivate()}d while a {@linkplain #profilingSessionOngoing profiling session is ongoing}.
 * A background thread consumes the {@link ActivationEvent}s and writes them to a {@linkplain #activationEventsBuffer direct buffer}
 * which is flushed to a {@linkplain #activationEventsFileChannel file}.
 * That is necessary because within a profiling session (which lasts 10s by default) there may be many more {@link ActivationEvent}s
 * than the ring buffer {@link #RING_BUFFER_SIZE can hold}.
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
 * After both the JFR file and the file containing the {@link ActivationEvent}s have been written,
 * it's now time to process them in tandem by correlating based on thread ids and timestamps.
 * The result of this correlation, performed by {@link #processTraces},
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
public class SamplingProfiler extends AbstractLifecycleListener implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfiler.class);
    private static final int ACTIVATION_EVENTS_IN_FILE = 1_000_000;
    private static final int MAX_STACK_DEPTH = 256;
    private static final int PRE_ALLOCATE_ACTIVATION_EVENTS_FILE_MB = 10;
    private static final int MAX_ACTIVATION_EVENTS_FILE_SIZE = ACTIVATION_EVENTS_IN_FILE * ActivationEvent.SERIALIZED_SIZE;
    private static final int ACTIVATION_EVENTS_BUFFER_SIZE = ActivationEvent.SERIALIZED_SIZE * 4 * 1024;
    private final EventTranslatorTwoArg<ActivationEvent, TraceContext, TraceContext> ACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorTwoArg<ActivationEvent, TraceContext, TraceContext>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContext active, TraceContext previouslyActive) {
                event.activation(active, Thread.currentThread().getId(), previouslyActive, nanoClock.nanoTime());
            }
        };
    private final EventTranslatorTwoArg<ActivationEvent, TraceContext, TraceContext> DEACTIVATION_EVENT_TRANSLATOR =
        new EventTranslatorTwoArg<ActivationEvent, TraceContext, TraceContext>() {
            @Override
            public void translateTo(ActivationEvent event, long sequence, TraceContext active, TraceContext previouslyActive) {
                event.deactivation(active, Thread.currentThread().getId(), previouslyActive, nanoClock.nanoTime());
            }
        };
    // sizeof(ActivationEvent) is 176B so the ring buffer should be around 880KiB
    static final int RING_BUFFER_SIZE = 4 * 1024;

    private final ProfilingConfiguration config;
    private final CoreConfiguration coreConfig;
    private final ScheduledExecutorService scheduler;
    private final Long2ObjectHashMap<CallTree.Root> profiledThreads = new Long2ObjectHashMap<>();
    private final RingBuffer<ActivationEvent> eventBuffer;
    private volatile boolean profilingSessionOngoing = false;
    private final Sequence sequence;
    private final ElasticApmTracer tracer;
    private final NanoClock nanoClock;
    private final ObjectPool<CallTree.Root> rootPool;
    private final ThreadMatcher threadMatcher = new ThreadMatcher();
    private final EventPoller<ActivationEvent> poller;
    @Nullable
    private File jfrFile;
    private boolean canDeleteJfrFile;
    private final WriteActivationEventToFileHandler writeActivationEventToFileHandler = new WriteActivationEventToFileHandler();
    @Nullable
    private JfrParser jfrParser;
    private volatile int profilingSessions;

    private final ByteBuffer activationEventsBuffer;
    /**
     * Used to efficiently write {@link #activationEventsBuffer} via {@link FileChannel#write(ByteBuffer)}
     */
    @Nullable
    private File activationEventsFile;
    private boolean canDeleteActivationEventsFile;

    @Nullable
    private FileChannel activationEventsFileChannel;
    private final ObjectPool<CallTree> callTreePool;
    private final TraceContext contextForLogging;

    private boolean previouslyEnabled = false;

    /**
     * Creates a sampling profiler using temporary files
     *
     * @param tracer    tracer
     * @param nanoClock clock
     */
    public SamplingProfiler(ElasticApmTracer tracer, NanoClock nanoClock) {
        this(tracer, nanoClock, null, null);
    }

    /**
     * Creates a sampling profiler, optionally relying on existing files.
     * <p>
     * This constructor is most likely used for tests that rely on a known set of files
     *
     * @param tracer               tracer
     * @param nanoClock            clock
     * @param activationEventsFile activation events file, if {@literal null} a temp file will be used
     * @param jfrFile              java flight recorder file, if {@literal null} a temp file will be used instead
     */
    public SamplingProfiler(final ElasticApmTracer tracer, NanoClock nanoClock, @Nullable File activationEventsFile, @Nullable File jfrFile) {
        this.tracer = tracer;
        this.config = tracer.getConfig(ProfilingConfiguration.class);
        this.coreConfig = tracer.getConfig(CoreConfiguration.class);
        this.scheduler = ExecutorUtils.createSingleThreadSchedulingDaemonPool("sampling-profiler");
        this.nanoClock = nanoClock;
        this.eventBuffer = createRingBuffer();
        this.sequence = new Sequence();
        // tells the ring buffer to not override slots which have not been read yet
        this.eventBuffer.addGatingSequences(sequence);
        this.poller = eventBuffer.newPoller();
        contextForLogging = TraceContext.with64BitId(tracer);
        this.callTreePool = ListBasedObjectPool.<CallTree>ofRecyclable(2 * 1024, new Allocator<CallTree>() {
            @Override
            public CallTree createInstance() {
                return new CallTree();
            }
        });
        // call tree roots are pooled so that fast activations/deactivations with no associated stack traces don't cause allocations
        this.rootPool = ListBasedObjectPool.<CallTree.Root>ofRecyclable(512, new Allocator<CallTree.Root>() {
            @Override
            public CallTree.Root createInstance() {
                return new CallTree.Root(tracer);
            }
        });
        this.jfrFile = jfrFile;
        activationEventsBuffer = ByteBuffer.allocateDirect(ACTIVATION_EVENTS_BUFFER_SIZE);
        this.activationEventsFile = activationEventsFile;
    }

    private synchronized void createFilesIfRequired() throws IOException {
        if (jfrFile == null || !jfrFile.exists()) {
            jfrFile = File.createTempFile("apm-traces-", ".jfr");
            jfrFile.deleteOnExit();
            canDeleteJfrFile = true;
        }
        if (activationEventsFile == null || !activationEventsFile.exists()) {
            activationEventsFile = File.createTempFile("apm-activation-events-", ".bin");
            activationEventsFile.deleteOnExit();
            canDeleteActivationEventsFile = true;
        }
        if (activationEventsFileChannel == null || !activationEventsFileChannel.isOpen()) {
            activationEventsFileChannel = FileChannel.open(activationEventsFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
        }
        if (activationEventsFileChannel.size() == 0) {
            preAllocate(activationEventsFileChannel, PRE_ALLOCATE_ACTIVATION_EVENTS_FILE_MB);
        }
    }

    // visible for benchmarks
    public void skipToEndOfActivationEventsFile() throws IOException {
        activationEventsFileChannel.position(activationEventsFileChannel.size());
    }

    /**
     * Makes sure that the first blocks of the file are contiguous to provide fast sequential access
     */
    private static void preAllocate(FileChannel channel, int mb) throws IOException {
        long initialPos = channel.position();
        ByteBuffer oneKb = ByteBuffer.allocate(1024);
        for (int i = 0; i < mb * 1024; i++) {
            channel.write(oneKb);
            ((Buffer) oneKb).clear();
        }
        channel.position(initialPos);
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
     * This and {@link #onDeactivation} are the only methods which are executed in a multi-threaded
     * context.
     * </p>
     *
     * @param activeSpan       the span which is about to be activated
     * @param previouslyActive the span which has previously been activated
     * @return {@code true}, if the event could be processed, {@code false} if the internal event queue is full which means the event has been discarded
     */
    public boolean onActivation(TraceContext activeSpan, @Nullable TraceContext previouslyActive) {
        if (profilingSessionOngoing) {
            if (previouslyActive == null) {
                AsyncProfiler.getInstance(config.getProfilerLibDirectory(), config.getAsyncProfilerSafeMode()).enableProfilingCurrentThread();
            }
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
     * This and {@link #onActivation} are the only methods which are executed in a multi-threaded
     * context.
     * </p>
     *
     * @param activeSpan       the span which is about to be activated
     * @param previouslyActive the span which has previously been activated
     * @return {@code true}, if the event could be processed, {@code false} if the internal event queue is full which means the event has been discarded
     */
    public boolean onDeactivation(TraceContext activeSpan, @Nullable TraceContext previouslyActive) {
        if (profilingSessionOngoing) {
            if (previouslyActive == null) {
                AsyncProfiler.getInstance(config.getProfilerLibDirectory(), config.getAsyncProfilerSafeMode()).disableProfilingCurrentThread();
            }
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

        boolean enabled = config.isProfilingEnabled() && tracer.isRunning();
        boolean hasBeenDisabled = previouslyEnabled && !enabled;
        previouslyEnabled = enabled;

        if (!enabled) {
            if (jfrParser != null) {
                jfrParser = null;
            }
            if (!scheduler.isShutdown()) {
                scheduler.schedule(this, config.getProfilingInterval().getMillis(), TimeUnit.MILLISECONDS);
            }

            if (hasBeenDisabled) {
                // only clear when going from enabled -> disabled state
                try {
                    clear();
                } catch (Throwable throwable) {
                    logger.error("Error while trying to clear profiler constructs", throwable);
                }
            }

            return;
        }


        // lazily create temporary files
        try {
            createFilesIfRequired();
        } catch (IOException e) {
            logger.error("unable to initialize profiling files", e);
            return;
        }

        TimeDuration sampleRate = config.getSamplingInterval();
        TimeDuration profilingDuration = config.getProfilingDuration();
        boolean postProcessingEnabled = config.isPostProcessingEnabled();

        setProfilingSessionOngoing(postProcessingEnabled);

        if (postProcessingEnabled) {
            logger.debug("Start full profiling session (async-profiler and agent processing)");
        } else {
            logger.debug("Start async-profiler profiling session");
        }
        try {
            profile(sampleRate, profilingDuration);
        } catch (Throwable t) {
            setProfilingSessionOngoing(false);
            logger.error("Stopping profiler", t);
            return;
        }
        logger.debug("End profiling session");

        boolean interrupted = Thread.currentThread().isInterrupted();
        boolean continueProfilingSession = config.isNonStopProfiling() && !interrupted && config.isProfilingEnabled() && postProcessingEnabled;
        setProfilingSessionOngoing(continueProfilingSession);

        if (!interrupted && !scheduler.isShutdown()) {
            long delay = config.getProfilingInterval().getMillis() - profilingDuration.getMillis();
            scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void profile(TimeDuration sampleRate, TimeDuration profilingDuration) throws Exception {
        AsyncProfiler asyncProfiler = AsyncProfiler.getInstance(config.getProfilerLibDirectory(), config.getAsyncProfilerSafeMode());
        try {
            String startCommand = "start,jfr,event=wall,cstack=n,interval=" + sampleRate.getMillis() + "ms,filter,file=" + jfrFile + ",safemode=" + config.getAsyncProfilerSafeMode();
            String startMessage = asyncProfiler.execute(startCommand);
            logger.debug(startMessage);
            if (!profiledThreads.isEmpty()) {
                restoreFilterState(asyncProfiler);
            }
            // Doesn't need to be atomic as this field is being updated only by a single thread
            //noinspection NonAtomicOperationOnVolatileField
            profilingSessions++;

            // When post-processing is disabled activation events are ignored, but we still need to invoke this method
            // as it is the one enforcing the sampling session duration. As a side effect it will also consume
            // residual activation events if post-processing is disabled dynamically
            consumeActivationEventsFromRingBufferAndWriteToFile(profilingDuration);

            String stopMessage = asyncProfiler.execute("stop");
            logger.debug(stopMessage);

            // When post-processing is disabled, jfr file will not be parsed and the heavy processing will not occur
            // as this method aborts when no activation events are buffered
            processTraces();
        } catch (InterruptedException | ClosedByInterruptException e) {
            try {
                asyncProfiler.stop();
            } catch (IllegalStateException ignore) {
            }
            Thread.currentThread().interrupt();
        }
    }

    /**
     * When doing continuous profiling (interval=duration),
     * we have to tell async-profiler which threads it should profile after re-starting it.
     */
    private void restoreFilterState(AsyncProfiler asyncProfiler) {
        threadMatcher.forEachThread(
            new ThreadMatcher.NonCapturingPredicate<Thread, Long2ObjectHashMap<?>.KeySet>() {
                @Override
                public boolean test(Thread thread, Long2ObjectHashMap<?>.KeySet profiledThreads) {
                    return profiledThreads.contains(thread.getId());
                }
            },
            profiledThreads.keySet(),
            new ThreadMatcher.NonCapturingConsumer<Thread, AsyncProfiler>() {
                @Override
                public void accept(Thread thread, AsyncProfiler asyncProfiler) {
                    asyncProfiler.enableProfilingThread(thread);
                }
            },
            asyncProfiler
        );
    }

    private void consumeActivationEventsFromRingBufferAndWriteToFile(TimeDuration profilingDuration) throws Exception {
        resetActivationEventBuffer();
        long threshold = System.currentTimeMillis() + profilingDuration.getMillis();
        long initialSleep = 100_000;
        long maxSleep = 10_000_000;
        long sleep = initialSleep;
        while (System.currentTimeMillis() < threshold && !Thread.currentThread().isInterrupted()) {
            if (activationEventsFileChannel.position() < MAX_ACTIVATION_EVENTS_FILE_SIZE) {
                EventPoller.PollState poll = consumeActivationEventsFromRingBufferAndWriteToFile();
                if (poll == EventPoller.PollState.PROCESSING) {
                    sleep = initialSleep;
                    // don't sleep, after consuming the events there might be new ones in the ring buffer
                } else {
                    if (sleep < maxSleep) {
                        sleep *= 2;
                    }
                    LockSupport.parkNanos(sleep);
                }
            } else {
                logger.warn("The activation events file is full. Try lowering the profiling_duration.");
                // the file is full, sleep the rest of the profilingDuration
                Thread.sleep(Math.max(0, threshold - System.currentTimeMillis()));
            }
        }
    }

    EventPoller.PollState consumeActivationEventsFromRingBufferAndWriteToFile() throws Exception {
        createFilesIfRequired();
        return poller.poll(writeActivationEventToFileHandler);
    }

    public void processTraces() throws IOException {
        if (jfrParser == null) {
            jfrParser = new JfrParser();
        }
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        createFilesIfRequired();

        long eof = startProcessingActivationEventsFile();
        if (eof == 0 && activationEventsBuffer.limit() == 0 && profiledThreads.isEmpty()) {
            logger.debug("No activation events during this period. Skip processing stack traces.");
            return;
        }
        long start = System.nanoTime();
        List<WildcardMatcher> excludedClasses = config.getExcludedClasses();
        List<WildcardMatcher> includedClasses = config.getIncludedClasses();
        if (config.isBackupDiagnosticFiles()) {
            backupDiagnosticFiles(eof);
        }
        try {
            jfrParser.parse(jfrFile, excludedClasses, includedClasses);
            final List<StackTraceEvent> stackTraceEvents = getSortedStackTraceEvents(jfrParser);
            if (logger.isDebugEnabled()) {
                logger.debug("Processing {} stack traces", stackTraceEvents.size());
            }
            List<StackFrame> stackFrames = new ArrayList<>();
            ElasticApmTracer tracer = this.tracer;
            ActivationEvent event = new ActivationEvent();
            long inferredSpansMinDuration = getInferredSpansMinDurationNs();
            for (StackTraceEvent stackTrace : stackTraceEvents) {
                processActivationEventsUpTo(stackTrace.nanoTime, event, eof);
                CallTree.Root root = profiledThreads.get(stackTrace.threadId);
                if (root != null) {
                    jfrParser.resolveStackTrace(stackTrace.stackTraceId, true, stackFrames, MAX_STACK_DEPTH);
                    if (stackFrames.size() == MAX_STACK_DEPTH) {
                        logger.debug("Max stack depth reached. Set profiling_included_classes or profiling_excluded_classes.");
                    }
                    // stack frames may not contain any Java frames
                    // see https://github.com/jvm-profiling-tools/async-profiler/issues/271#issuecomment-582430233
                    if (!stackFrames.isEmpty()) {
                        try {
                            root.addStackTrace(tracer, stackFrames, stackTrace.nanoTime, callTreePool, inferredSpansMinDuration);
                        } catch (Exception e) {
                            logger.warn("Removing call tree for thread {} because of exception while adding a stack trace: {} {}",
                                stackTrace.threadId, e.getClass(), e.getMessage());
                            logger.debug(e.getMessage(), e);
                            profiledThreads.remove(stackTrace.threadId);
                        }
                    }
                }
                stackFrames.clear();
            }
            // process all activation events that happened after the last stack trace event
            // otherwise we may miss root deactivations
            processActivationEventsUpTo(System.nanoTime(), event, eof);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing traces took {}µs", (System.nanoTime() - start) / 1000);
            }
            jfrParser.resetState();
            resetActivationEventBuffer();
        }
    }

    private void backupDiagnosticFiles(long eof) throws IOException {
        String now = String.format("%tFT%<tT.%<tL", new Date());
        Path profilerDir = Paths.get(System.getProperty("java.io.tmpdir"), "profiler");
        profilerDir.toFile().mkdir();

        try (FileChannel activationsFile = FileChannel.open(profilerDir.resolve(now + "-activations.dat"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            if (eof > 0) {
                activationEventsFileChannel.transferTo(0, eof, activationsFile);
            } else {
                int position = activationEventsBuffer.position();
                activationsFile.write(activationEventsBuffer);
                activationEventsBuffer.position(position);
            }
        }
        Files.copy(jfrFile.toPath(), profilerDir.resolve(now + "-traces.jfr"));
    }

    private long getInferredSpansMinDurationNs() {
        return Math.max(config.getInferredSpansMinDuration().getMillis(), coreConfig.getSpanMinDuration().getMillis()) * 1_000_000;
    }

    /**
     * Returns stack trace events of relevant threads sorted by timestamp.
     * The events in the JFR file are not in order.
     * Even for the same thread, a more recent event might come before an older event.
     * In order to be able to correlate stack trace events and activation events, both need to be in order.
     * <p>
     * Returns only events for threads where at least one activation happened (because only those are profiled by async-profiler)
     */
    private List<StackTraceEvent> getSortedStackTraceEvents(JfrParser jfrParser) throws IOException {
        final List<StackTraceEvent> stackTraceEvents = new ArrayList<>();
        jfrParser.consumeStackTraces(new JfrParser.StackTraceConsumer() {
            @Override
            public void onCallTree(long threadId, long stackTraceId, long nanoTime) {
                stackTraceEvents.add(new StackTraceEvent(nanoTime, stackTraceId, threadId));
            }
        });
        Collections.sort(stackTraceEvents);
        return stackTraceEvents;
    }

    void processActivationEventsUpTo(long timestamp, long eof) throws IOException {
        processActivationEventsUpTo(timestamp, new ActivationEvent(), eof);
    }

    public void processActivationEventsUpTo(long timestamp, ActivationEvent event, long eof) throws IOException {
        FileChannel activationEventsFileChannel = this.activationEventsFileChannel;
        ByteBuffer buf = activationEventsBuffer;
        long previousTimestamp = 0;
        while (buf.hasRemaining() || activationEventsFileChannel.position() < eof) {
            if (!buf.hasRemaining()) {
                readActivationEventsToBuffer(activationEventsFileChannel, eof, buf);
            }
            long eventTimestamp = peekLong(buf);
            if (eventTimestamp < previousTimestamp && logger.isDebugEnabled()) {
                logger.debug("Timestamp of current activation event ({}) is lower than the one from the previous event ({})", eventTimestamp, previousTimestamp);
            }
            previousTimestamp = eventTimestamp;
            if (eventTimestamp <= timestamp) {
                event.deserialize(buf);
                try {
                    event.handle(this);
                } catch (Exception e) {
                    logger.warn("Removing call tree for thread {} because of exception while handling activation event: {} {}",
                        event.threadId, e.getClass(), e.getMessage());
                    logger.debug(e.getMessage(), e);
                    profiledThreads.remove(event.threadId);
                }
            } else {
                return;
            }
        }
    }

    private void readActivationEventsToBuffer(FileChannel activationEventsFileChannel, long eof, ByteBuffer byteBuffer) throws IOException {
        Buffer buf = byteBuffer;
        buf.clear();
        long remaining = eof - activationEventsFileChannel.position();
        activationEventsFileChannel.read(byteBuffer);
        buf.flip();
        if (remaining < buf.capacity()) {
            buf.limit((int) remaining);
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

    public void resetActivationEventBuffer() throws IOException {
        ((Buffer) activationEventsBuffer).clear();
        if (activationEventsFileChannel != null && activationEventsFileChannel.isOpen()) {
            activationEventsFileChannel.position(0L);
        }
    }

    private void flushActivationEvents() throws IOException {
        if (activationEventsBuffer.position() > 0) {
            ((Buffer) activationEventsBuffer).flip();
            activationEventsFileChannel.write(activationEventsBuffer);
            ((Buffer) activationEventsBuffer).clear();
        }
    }

    long startProcessingActivationEventsFile() throws IOException {
        Buffer activationEventsBuffer = this.activationEventsBuffer;
        if (activationEventsFileChannel.position() > 0) {
            flushActivationEvents();
            activationEventsBuffer.limit(0);
        } else {
            activationEventsBuffer.flip();
        }
        long eof = activationEventsFileChannel.position();
        activationEventsFileChannel.position(0);
        return eof;
    }

    void copyFromFiles(Path activationEvents, Path traces) throws IOException {
        createFilesIfRequired();

        FileChannel otherActivationsChannel = FileChannel.open(activationEvents, READ);
        activationEventsFileChannel.transferFrom(otherActivationsChannel, 0, otherActivationsChannel.size());
        activationEventsFileChannel.position(otherActivationsChannel.size());
        FileChannel otherTracesChannel = FileChannel.open(traces, READ);
        FileChannel.open(jfrFile.toPath(), WRITE).transferFrom(otherTracesChannel, 0, otherTracesChannel.size());
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        scheduler.submit(this);
    }

    @Override
    public void stop() throws Exception {
        // cancels/interrupts the profiling thread
        // implicitly clears profiled threads
        ExecutorUtils.shutdownAndWaitTermination(scheduler);

        if (activationEventsFileChannel != null) {
            activationEventsFileChannel.close();
        }

        if (jfrFile != null && canDeleteJfrFile) {
            jfrFile.delete();
        }
        if (activationEventsFile != null && canDeleteActivationEventsFile) {
            activationEventsFile.delete();
        }
    }

    void setProfilingSessionOngoing(boolean profilingSessionOngoing) {
        this.profilingSessionOngoing = profilingSessionOngoing;
        if (!profilingSessionOngoing) {
            clearProfiledThreads();
        } else if (!profiledThreads.isEmpty() && logger.isDebugEnabled()) {
            logger.debug("Retaining {} call tree roots", profiledThreads.size());
        }
    }

    public void clearProfiledThreads() {
        for (CallTree.Root root : profiledThreads.values()) {
            root.recycle(callTreePool, rootPool);
        }
        profiledThreads.clear();
    }

    // for testing
    CallTree.Root getRoot() {
        return profiledThreads.get(Thread.currentThread().getId());
    }

    void clear() throws IOException {
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
        profiledThreads.clear();
        callTreePool.clear();
        rootPool.clear();
    }

    int getProfilingSessions() {
        return profilingSessions;
    }
    // --

    public static class StackTraceEvent implements Comparable<StackTraceEvent> {
        private final long nanoTime;
        private final long stackTraceId;
        private final long threadId;

        private StackTraceEvent(long nanoTime, long stackTraceId, long threadId) {
            this.nanoTime = nanoTime;
            this.stackTraceId = stackTraceId;
            this.threadId = threadId;
        }

        public long getThreadId() {
            return threadId;
        }

        public long getNanoTime() {
            return nanoTime;
        }

        public long getStackTraceId() {
            return stackTraceId;
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

        public void activation(TraceContext context, long threadId, @Nullable TraceContext previousContext, long nanoTime) {
            set(context, threadId, true, previousContext != null ? previousContext : null, nanoTime);
        }

        public void deactivation(TraceContext context, long threadId, @Nullable TraceContext previousContext, long nanoTime) {
            set(context, threadId, false, previousContext != null ? previousContext : null, nanoTime);
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
            if (logger.isDebugEnabled()) {
                logger.debug("Handling event timestamp={} root={} threadId={} activation={}", timestamp, rootContext, threadId, activation);
            }
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
                    if (logger.isDebugEnabled()) {
                        logger.debug("Handling activation for thread {}", threadId);
                    }
                    root.onActivation(traceContextBuffer, timestamp);
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Illegal state when handling activation event for thread {}: no root found for this thread", threadId);
                }
            }
        }

        private void startProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root root = CallTree.createRoot(samplingProfiler.rootPool, traceContextBuffer, serviceName, timestamp);
            if (logger.isDebugEnabled()) {
                logger.debug("Create call tree ({}) for thread {}", deserialize(samplingProfiler, traceContextBuffer), threadId);
            }

            CallTree.Root orphaned = samplingProfiler.profiledThreads.put(threadId, root);
            if (orphaned != null) {
                if (logger.isDebugEnabled()) {
                    logger.warn("Illegal state when stopping profiling for thread {}: orphaned root", threadId);
                }
                orphaned.recycle(samplingProfiler.callTreePool, samplingProfiler.rootPool);
            }
        }

        private TraceContext deserialize(SamplingProfiler samplingProfiler, byte[] traceContextBuffer) {
            samplingProfiler.contextForLogging.deserialize(traceContextBuffer, null);
            return samplingProfiler.contextForLogging;
        }

        private void handleDeactivationEvent(SamplingProfiler samplingProfiler) {
            if (rootContext) {
                stopProfiling(samplingProfiler);
            } else {
                CallTree.Root root = samplingProfiler.profiledThreads.get(threadId);
                if (root != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Handling deactivation for thread {}", threadId);
                    }
                    root.onDeactivation(traceContextBuffer, previousContextBuffer, timestamp);
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Illegal state when handling deactivation event for thread {}: no root found for this thread", threadId);
                }
            }
        }

        private void stopProfiling(SamplingProfiler samplingProfiler) {
            CallTree.Root callTree = samplingProfiler.profiledThreads.get(threadId);
            if (callTree != null && callTree.getRootContext().traceIdAndIdEquals(traceContextBuffer)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("End call tree ({}) for thread {}", deserialize(samplingProfiler, traceContextBuffer), threadId);
                }
                samplingProfiler.profiledThreads.remove(threadId);
                try {
                    callTree.end(samplingProfiler.callTreePool, samplingProfiler.getInferredSpansMinDurationNs());
                    int createdSpans = callTree.spanify();
                    if (logger.isDebugEnabled()) {
                        if (createdSpans > 0) {
                            logger.debug("Created spans ({}) for thread {}", createdSpans, threadId);
                        } else {
                            logger.debug("Created no spans for thread {} (count={})", threadId, callTree.getCount());
                        }
                    }
                } finally {
                     callTree.recycle(samplingProfiler.callTreePool, samplingProfiler.rootPool);
                }
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
        public boolean onEvent(ActivationEvent event, long sequence, boolean endOfBatch) throws IOException {
            if (endOfBatch) {
                SamplingProfiler.this.sequence.set(sequence);
            }
            if (activationEventsFileChannel.size() < MAX_ACTIVATION_EVENTS_FILE_SIZE) {
                event.serialize(activationEventsBuffer);
                if (!activationEventsBuffer.hasRemaining()) {
                    flushActivationEvents();
                }
                return true;
            }
            return false;
        }
    }
}
