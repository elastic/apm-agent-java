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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Converts a sequence of stack traces into a tree structure of method calls.
 * <pre>
 *             count
 *  b b     a      4
 * aaaa ─►  ├─b    1
 *          └─b    1
 * </pre>
 * <p>
 * It also stores information about which span is the parent of a particular call tree node,
 * based on which span has been {@linkplain ElasticApmTracer#getActive() active} at that time.
 * </p>
 * <p>
 * This allows to {@linkplain Root#spanify() infer spans from the call tree} which have the correct parent/child relationships
 * with the regular spans.
 * </p>
 */
public class CallTree implements Recyclable {

    private static final int INITIAL_CHILD_SIZE = 2;
    @Nullable
    private CallTree parent;
    protected int count;
    private List<CallTree> children = new ArrayList<>(INITIAL_CHILD_SIZE);
    @Nullable
    private StackFrame frame;
    protected long start;
    private long lastSeen;
    private boolean ended;
    private long activationTimestamp = -1;
    /**
     * The context of the transaction or span which is the direct parent of this call tree node.
     * Used in {@link #spanify(Root, TraceContext)} to override the parent.
     */
    @Nullable
    private TraceContext activeContextOfDirectParent;
    private long deactivationTimestamp = -1;
    private boolean isSpan;

    public CallTree() {
    }

    public void set(@Nullable CallTree parent, StackFrame frame, long nanoTime) {
        this.parent = parent;
        this.frame = frame;
        this.start = nanoTime;
    }

    public void activation(TraceContext traceContext, long activationTimestamp) {
        this.activeContextOfDirectParent = traceContext;
        this.activationTimestamp = activationTimestamp;
    }

    protected void handleDeactivation(TraceContext deactivatedSpan, long activationTimestamp, long deactivationTimestamp) {
        if (deactivatedSpan.equals(activeContextOfDirectParent)) {
            this.deactivationTimestamp = deactivationTimestamp;

        } else {
            CallTree lastChild = getLastChild();
            if (lastChild != null) {
                lastChild.handleDeactivation(deactivatedSpan, activationTimestamp, deactivationTimestamp);
            }
        }
        // if an actual child span is deactivated after this call tree node has ended
        // it means that this node has actually ended at least at the same point, if not after, the actual span has been deactivated
        //
        // [a(inferred)]    ─► [a(inferred)  ] <- set end timestamp to timestamp of deactivation of b
        // └─[b(actual)  ]     └─[b(actual)  ]
        // see also CallTreeTest::testDectivationAfterEnd
        if (happenedDuring(activationTimestamp) && happenedAfter(deactivationTimestamp)) {
            lastSeen = deactivationTimestamp;
        }
    }

    private boolean happenedDuring(long timestamp) {
        return start <= timestamp && timestamp <= lastSeen;
    }

    private boolean happenedAfter(long timestamp) {
        return lastSeen < timestamp;
    }

    public static CallTree.Root createRoot(ObjectPool<CallTree.Root> rootPool, byte[] traceContext, @Nullable String serviceName, long nanoTime) {
        CallTree.Root root = rootPool.createInstance();
        root.set(traceContext, serviceName, nanoTime);
        return root;
    }

    /**
     * Adds a single stack trace to the call tree which either updates the {@link #lastSeen} timestamp of an existing call tree node,
     * {@linkplain #end(ObjectPool, long) ends} a node, or {@linkplain #addChild adds a new child}.
     *
     * @param stackFrames         the stack trace which is iterated over in reverse order
     * @param index               the current index of {@code stackFrames}
     * @param activeSpan          the trace context of the currently {@linkplain ElasticApmTracer#getActive()} active transaction/span
     * @param activationTimestamp the timestamp of when {@code traceContext} has been activated
     * @param nanoTime            the timestamp of when this stack trace has been recorded
     * @param callTreePool
     * @param minDurationNs
     */
    protected void addFrame(List<StackFrame> stackFrames, int index, @Nullable TraceContext activeSpan, long activationTimestamp, long nanoTime, ObjectPool<CallTree> callTreePool, long minDurationNs) {
        count++;
        lastSeen = nanoTime;
        //     c ee   <- traceContext not set - they are not a child of the active span but the frame below them
        //   bbb dd   <- traceContext set
        //   ------   <- all new CallTree during this period should have the traceContext set
        // a aaaaaa a
        //  |      |
        // active  deactive

        // this branch is already aware of the activation
        // this means the provided activeSpan is not a direct parent of new child nodes
        if (Objects.equals(this.activeContextOfDirectParent, activeSpan)) {
            activeSpan = null;
        }

        // non-last children are already ended by definition
        CallTree lastChild = getLastChild();
        // if the frame corresponding to the last child is not in the stack trace
        // it's assumed to have ended one tick ago
        boolean endChild = true;
        if (index >= 1) {
            final StackFrame frame = stackFrames.get(--index);
            if (lastChild != null) {
                if (!lastChild.isEnded() && frame.equals(lastChild.frame)) {
                    lastChild.addFrame(stackFrames, index, activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs);
                    endChild = false;
                } else {
                    addChild(frame, stackFrames, index, activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs);
                }
            } else {
                addChild(frame, stackFrames, index, activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs);
            }
        }
        if (lastChild != null && !lastChild.isEnded() && endChild) {
            lastChild.end(callTreePool, minDurationNs);
        }
    }

    private void addChild(StackFrame frame, List<StackFrame> stackFrames, int index, @Nullable TraceContext traceContext, long activationTimestamp, long nanoTime, ObjectPool<CallTree> callTreePool, long minDurationNs) {
        CallTree callTree = callTreePool.createInstance();
        callTree.set(this, frame, nanoTime);
        if (traceContext != null) {
            callTree.activation(traceContext, activationTimestamp);
        }
        children.add(callTree);
        callTree.addFrame(stackFrames, index, null, activationTimestamp, nanoTime, callTreePool, minDurationNs);
    }

    long getDurationUs() {
        return getDurationNs() / 1000;
    }

    private long getDurationNs() {
        return lastSeen - start;
    }

    public int getCount() {
        return count;
    }

    @Nullable
    public StackFrame getFrame() {
        return frame;
    }

    public List<CallTree> getChildren() {
        return children;
    }

    void end(ObjectPool<CallTree> pool, long minDurationNs) {
        ended = true;
        // if the parent span has already been deactivated before this call tree node has ended
        // it means that this node is actually the parent of the already deactivated span
        //                     make b parent of a and pre-date the start of b to the activation of a
        // [c        ]    ──┐  [a(inferred) ]
        // └[a(inferred)]   │  [b(inferred)]
        //  [b(infer.) ]    └► [c        ]
        //  └─[d(i.)]          └──[d(i.)]
        // see also CallTreeTest::testDectivationBeforeEnd
        if (deactivationHappenedBeforeEnd()) {
            start = Math.min(activationTimestamp, start);
            List<CallTree> callTrees = getChildren();
            for (int i = 0, size = callTrees.size(); i < size; i++) {
                CallTree child = callTrees.get(i);
                child.activation(activeContextOfDirectParent, activationTimestamp);
                child.deactivationTimestamp = deactivationTimestamp;
                // re-run this logic for all children, even if they have already ended
                child.end(pool, minDurationNs);
            }
            activeContextOfDirectParent = null;
            activationTimestamp = -1;
            deactivationTimestamp = -1;
        }
        if (parent != null && (count == 1 || isFasterThan(minDurationNs))) {
            parent.children.remove(this);
            recycle(pool);
        } else {
            CallTree lastChild = getLastChild();
            if (lastChild != null && !lastChild.isEnded()) {
                lastChild.end(pool, minDurationNs);
            }
        }
    }

    private boolean isFasterThan(long minDurationNs) {
        return getDurationNs() < minDurationNs;
    }

    private boolean deactivationHappenedBeforeEnd() {
        return activeContextOfDirectParent != null && deactivationTimestamp > -1 && lastSeen > deactivationTimestamp;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Returns {@code true} if this node has just one child and no self time.
     *
     * <pre>
     *  c
     *  b  <- b is a pillar
     * aaa
     * </pre>
     */
    private boolean isPillar() {
        return children.size() == 1 && children.get(0).count == count;
    }

    @Nullable
    public CallTree getLastChild() {
        return children.size() > 0 ? children.get(children.size() - 1) : null;
    }

    public boolean isEnded() {
        return ended;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            toString(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    private void toString(Appendable out) throws IOException {
        toString(out, 0);
    }

    private void toString(Appendable out, int level) throws IOException {
        for (int i = 0; i < level; i++) {
            out.append("  ");
        }
        out.append(frame != null ? frame.getClassName() : "null")
            .append('.')
            .append(frame != null ? frame.getMethodName() : "null")
            .append(' ').append(Integer.toString(count))
            .append('\n');
        for (CallTree node : children) {
            node.toString(out, level + 1);
        }
    }

    void spanify(CallTree.Root root, TraceContext parentContext) {
        if (activeContextOfDirectParent != null) {
            parentContext = activeContextOfDirectParent;
        }
        Span span = null;
        if (!isPillar() || isLeaf()) {
            span = asSpan(root, parentContext);
            this.isSpan = true;
        }
        List<CallTree> children = getChildren();
        for (int i = 0, size = children.size(); i < size; i++) {
            children.get(i).spanify(root, span != null ? span.getTraceContext() : parentContext);
        }
        if (span != null) {
            span.end(span.getTimestamp() + getDurationUs());
        }
    }

    protected Span asSpan(Root root, TraceContext parentContext) {
        Span span = parentContext.createSpan(root.getEpochMicros(this.start))
            .withType("app")
            .withSubtype("inferred");

        frame.appendSimpleClassName(span.getNameForSerialization());
        span.appendToName("#");
        span.appendToName(frame.getMethodName());

        // we're not interested in the very bottom of the stack which contains things like accepting and handling connections
        if (!root.rootContext.equals(parentContext)) {
            // we're never spanifying the root
            assert this.parent != null;
            List<StackFrame> stackTrace = new ArrayList<>();
            this.parent.fillStackTrace(stackTrace);
            span.setStackTrace(stackTrace);
        } else {
            span.setStackTrace(Collections.<StackFrame>emptyList());
        }
        return span;
    }

    /**
     * Fill in the stack trace up to the parent span
     */
    private void fillStackTrace(List<StackFrame> stackTrace) {
        if (parent != null && !this.isSpan) {
            stackTrace.add(frame);
            parent.fillStackTrace(stackTrace);
        }
    }

    public void recycle(ObjectPool<CallTree> pool) {
        List<CallTree> children = this.children;
        for (int i = 0, size = children.size(); i < size; i++) {
            children.get(i).recycle(pool);
        }
        pool.recycle(this);
    }

    @Override
    public void resetState() {
        parent = null;
        count = 0;
        frame = null;
        start = 0;
        lastSeen = 0;
        ended = false;
        activationTimestamp = -1;
        activeContextOfDirectParent = null;
        deactivationTimestamp = -1;
        isSpan = false;
        if (children.size() > INITIAL_CHILD_SIZE) {
            // the overwhelming majority of call tree nodes has either one or two children
            // don't let outliers grow all lists in the pool over time
            children = new ArrayList<>(INITIAL_CHILD_SIZE);
        } else {
            children.clear();
        }
    }

    /**
     * A special kind of a {@link CallTree} node which represents the root of the call tree.
     * This acts as the interface to the outside to add new nodes to the tree or to update existing ones by
     * {@linkplain #addStackTrace adding stack traces}.
     */
    public static class Root extends CallTree implements Recyclable {
        private static final Logger logger = LoggerFactory.getLogger(Root.class);
        private static final StackFrame ROOT_FRAME = new StackFrame("root", "root");
        /**
         * The context of the thread root,
         * mostly a transaction or a span which got activated by {@link co.elastic.apm.agent.impl.async.SpanInScopeRunnableWrapper}
         */
        protected TraceContext rootContext;
        /**
         * The context of the transaction or span which is currently {@link ElasticApmTracer#getActive() active}.
         * This is lazily deserialized from {@link #activeSpanSerialized} if there's an actual {@linkplain #addStackTrace stack trace}
         * for this activation.
         */
        @Nullable
        private TraceContext activeSpan;
        /**
         * The timestamp of when {@link #activeSpan} got activated
         */
        private long activationTimestamp = -1;
        /**
         * The context of the transaction or span which is currently {@link ElasticApmTracer#getActive() active},
         * in its {@linkplain TraceContext#serialize serialized} form.
         */
        private byte[] activeSpanSerialized = new byte[TraceContext.SERIALIZED_LENGTH];

        public Root(ElasticApmTracer tracer) {
            this.rootContext = TraceContext.with64BitId(tracer);
        }

        private void set(byte[] traceContext, @Nullable String serviceName, long nanoTime) {
            super.set(null, ROOT_FRAME, nanoTime);
            this.rootContext.deserialize(traceContext, serviceName);
            setActiveSpan(traceContext, nanoTime);
        }

        public void setActiveSpan(byte[] activeSpanSerialized, long timestamp) {
            activationTimestamp = timestamp;
            System.arraycopy(activeSpanSerialized, 0, this.activeSpanSerialized, 0, activeSpanSerialized.length);
            this.activeSpan = null;
        }

        public void onActivation(byte[] active, long timestamp) {
            setActiveSpan(active, timestamp);
        }

        public void onDeactivation(byte[] active, long timestamp) {
            if (activeSpan != null) {
                handleDeactivation(activeSpan, activationTimestamp, timestamp);
            } else {
                logger.debug("tried to handle deactivation without an active span");
            }
            setActiveSpan(active, timestamp);
        }

        public void addStackTrace(ElasticApmTracer tracer, List<StackFrame> stackTrace, long nanoTime, ObjectPool<CallTree> callTreePool, long minDurationNs) {
            // only "materialize" trace context if there's actually an associated stack trace to the activation
            // avoids allocating a TraceContext for very short activations which have no effect on the CallTree anyway
            if (activeSpan == null) {
                activeSpan = TraceContext.with64BitId(tracer);
                activeSpan.deserialize(activeSpanSerialized, rootContext.getServiceName());
            }
            addFrame(stackTrace, stackTrace.size(), activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs);
        }

        /**
         * Creates spans for call tree nodes if they are either not a {@linkplain #isPillar() pillar} or are a {@linkplain #isLeaf() leaf}.
         * Nodes which are not converted to {@link Span}s are part of the {@link Span#stackFrames} for the nodes which do get converted to a span.
         * <p>
         * Parent/child relationships with the regular spans are maintained.
         * One exception is that an inferred span can't be the parent of a regular span.
         * That is because the regular spans have already been reported once the inferred spans are created.
         * In the future, we might make it possible to update the {@link TraceContext#parentId}
         * of a regular span so that it correctly reflects being a child of an inferred span.
         * </p>
         */
        public void spanify() {
            List<CallTree> callTrees = getChildren();
            for (int i = 0, size = callTrees.size(); i < size; i++) {
                callTrees.get(i).spanify(this, rootContext);
            }
        }

        public TraceContext getRootContext() {
            return rootContext;
        }

        public long getEpochMicros(long nanoTime) {
            return rootContext.getClock().getEpochMicros(nanoTime);
        }

        public void recycle(ObjectPool<CallTree> pool) {
            List<CallTree> children = getChildren();
            for (int i = 0, size = children.size(); i < size; i++) {
                children.get(i).recycle(pool);
            }
        }

        @Override
        public void resetState() {
            super.resetState();
            activeSpan = null;
            activationTimestamp = -1;
        }
    }
}
