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

import co.elastic.apm.agent.collections.LongList;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.profiler.collections.LongHashSet;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Converts a sequence of stack traces into a tree structure of method calls.
 * <pre>
 *             count
 *  b b     a      4
 * aaaa ──► ├─b    1
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
     * Used in {@link #spanify} to override the parent.
     */
    @Nullable
    private TraceContext activeContextOfDirectParent;
    private long deactivationTimestamp = -1;
    private boolean isSpan;
    private int depth;
    /**
     * @see co.elastic.apm.agent.impl.transaction.AbstractSpan#childIds
     */
    @Nullable
    private LongList childIds;
    @Nullable
    private LongList maybeChildIds;

    public CallTree() {
    }

    public void set(@Nullable CallTree parent, StackFrame frame, long nanoTime) {
        this.parent = parent;
        this.frame = frame;
        this.start = nanoTime;
        if (parent != null) {
            this.depth = parent.depth + 1;
        }
    }

    public boolean isSuccessor(CallTree parent) {
        if (depth > parent.depth) {
            return getNthParent(depth - parent.depth) == parent;
        }
        return false;
    }

    @Nullable
    public CallTree getNthParent(int n) {
        CallTree parent = this;
        for (int i = 0; i < n; i++) {
            if (parent != null) {
                parent = parent.parent;
            } else {
                return null;
            }
        }
        return parent;
    }

    public void activation(TraceContext traceContext, long activationTimestamp) {
        this.activeContextOfDirectParent = traceContext;
        this.activationTimestamp = activationTimestamp;
    }

    protected void handleDeactivation(TraceContext deactivatedSpan, long activationTimestamp, long deactivationTimestamp) {
        if (deactivatedSpan.idEquals(activeContextOfDirectParent)) {
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
        // [a(inferred)]    ─► [a(inferred)  ] ← set end timestamp to timestamp of deactivation of b
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
     * {@linkplain #end ends} a node, or {@linkplain #addChild adds a new child}.
     *
     * @param stackFrames         the stack trace which is iterated over in reverse order
     * @param index               the current index of {@code stackFrames}
     * @param activeSpan          the trace context of the currently {@linkplain ElasticApmTracer#getActive()} active transaction/span
     * @param activationTimestamp the timestamp of when {@code traceContext} has been activated
     * @param nanoTime            the timestamp of when this stack trace has been recorded
     * @param callTreePool
     * @param minDurationNs
     * @param root
     */
    protected CallTree addFrame(List<StackFrame> stackFrames, int index, @Nullable TraceContext activeSpan, long activationTimestamp, long nanoTime, ObjectPool<CallTree> callTreePool, long minDurationNs, Root root) {
        count++;
        lastSeen = nanoTime;
        //     c ee   ← traceContext not set - they are not a child of the active span but the frame below them
        //   bbb dd   ← traceContext set
        //   ------   ← all new CallTree during this period should have the traceContext set
        // a aaaaaa a
        //  |      |
        // active  deactive

        // this branch is already aware of the activation
        // this means the provided activeSpan is not a direct parent of new child nodes
        if (activeSpan != null && this.activeContextOfDirectParent != null && this.activeContextOfDirectParent.idEquals(activeSpan)) {
            activeSpan = null;
        }

        // non-last children are already ended by definition
        CallTree lastChild = getLastChild();
        // if the frame corresponding to the last child is not in the stack trace
        // it's assumed to have ended one tick ago
        CallTree topOfStack = this;
        boolean endChild = true;
        if (index >= 1) {
            final StackFrame frame = stackFrames.get(--index);
            if (lastChild != null) {
                if (!lastChild.isEnded() && frame.equals(lastChild.frame)) {
                    topOfStack = lastChild.addFrame(stackFrames, index, activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs, root);
                    endChild = false;
                } else {
                    topOfStack = addChild(frame, stackFrames, index, activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs, root);
                }
            } else {
                topOfStack = addChild(frame, stackFrames, index, activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs, root);
            }
        }
        if (lastChild != null && !lastChild.isEnded() && endChild) {
            lastChild.end(callTreePool, minDurationNs, root);
        }
        transferMaybeChildIdsToChildIds();
        return topOfStack;
    }

    /**
     * This method is called when we know for sure that the maybe child ids are actually belonging to this call tree.
     * This is the case after we've seen another frame represented by this call tree.
     *
     * @see #addMaybeChildId(long)
     */
    private void transferMaybeChildIdsToChildIds() {
        if (maybeChildIds != null) {
            if (childIds == null) {
                childIds = maybeChildIds;
                maybeChildIds = null;
            } else {
                childIds.addAll(maybeChildIds);
                maybeChildIds.clear();
            }
        }
    }

    private CallTree addChild(StackFrame frame, List<StackFrame> stackFrames, int index, @Nullable TraceContext traceContext, long activationTimestamp, long nanoTime, ObjectPool<CallTree> callTreePool, long minDurationNs, Root root) {
        CallTree callTree = callTreePool.createInstance();
        callTree.set(this, frame, nanoTime);
        if (traceContext != null) {
            callTree.activation(traceContext, activationTimestamp);
        }
        children.add(callTree);
        return callTree.addFrame(stackFrames, index, null, activationTimestamp, nanoTime, callTreePool, minDurationNs, root);
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

    protected void end(ObjectPool<CallTree> pool, long minDurationNs, Root root) {
        ended = true;
        // if the parent span has already been deactivated before this call tree node has ended
        // it means that this node is actually the parent of the already deactivated span
        //                     make b parent of a and pre-date the start of b to the activation of a
        // [a(inferred)   ]     [a(inferred)   ]
        //  [1        ]     ──┐  [b(inferred) ]
        //  └[b(inferred)]    │  [c(inferred)]
        //   [c(infer.) ]     └► [1        ]
        //   └─[d(i.)]           └──[d(i.)]
        // see also CallTreeTest::testDeactivationBeforeEnd
        if (deactivationHappenedBeforeEnd()) {
            start = Math.min(activationTimestamp, start);
            if (parent != null) {
                // we know there's always exactly one activation in the parent's childIds
                // that needs to be transferred to this call tree node
                // in the above example, 1's child id would be first transferred from a to b and then from b to c
                // this ensures that the UI knows that c is the parent of 1
                parent.giveLastChildIdTo(this);
            }

            List<CallTree> callTrees = getChildren();
            for (int i = 0, size = callTrees.size(); i < size; i++) {
                CallTree child = callTrees.get(i);
                child.activation(activeContextOfDirectParent, activationTimestamp);
                child.deactivationTimestamp = deactivationTimestamp;
                // re-run this logic for all children, even if they have already ended
                child.end(pool, minDurationNs, root);
            }
            activeContextOfDirectParent = null;
            activationTimestamp = -1;
            deactivationTimestamp = -1;
        }
        if (parent != null && isTooFast(minDurationNs)) {
            root.previousTopOfStack = parent;
            parent.removeChild(pool, this);
        } else {
            CallTree lastChild = getLastChild();
            if (lastChild != null && !lastChild.isEnded()) {
                lastChild.end(pool, minDurationNs, root);
            }
        }
    }

    private boolean isTooFast(long minDurationNs) {
        return count == 1 || isFasterThan(minDurationNs);
    }

    private void removeChild(ObjectPool<CallTree> pool, CallTree child) {
        children.remove(child);
        child.recursiveGiveChildIdsTo(this);
        child.recycle(pool);
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
     *  b  ← b is a pillar
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

    int spanify(CallTree.Root root, TraceContext parentContext) {
        int createdSpans = 0;
        if (activeContextOfDirectParent != null) {
            parentContext = activeContextOfDirectParent;
        }
        Span span = null;
        if (!isPillar() || isLeaf()) {
            createdSpans++;
            span = asSpan(root, parentContext);
            this.isSpan = true;
        }
        List<CallTree> children = getChildren();
        for (int i = 0, size = children.size(); i < size; i++) {
            createdSpans += children.get(i).spanify(root, span != null ? span.getTraceContext() : parentContext);
        }
        if (span != null) {
            span.end(span.getTimestamp() + getDurationUs());
        }
        return createdSpans;
    }

    protected Span asSpan(Root root, TraceContext parentContext) {
        transferMaybeChildIdsToChildIds();
        Span span = parentContext.createSpan(root.getEpochMicros(this.start))
            .withType("app")
            .withSubtype("inferred")
            .withChildIds(childIds);

        frame.appendSimpleClassName(span.getNameForSerialization());
        span.appendToName("#");
        span.appendToName(frame.getMethodName());

        // we're not interested in the very bottom of the stack which contains things like accepting and handling connections
        if (!root.rootContext.idEquals(parentContext)) {
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

    /**
     * Recycles this subtree to the provided pool recursively.
     * Note that this method ends by recycling {@code this} node (i.e. - this subtree root), which means that
     * <b>the caller of this method should make sure that no reference to this object is held anywhere</b>.
     * <p>ALSO NOTE: MAKE SURE NOT TO CALL THIS METHOD FOR {@link CallTree.Root} INSTANCES.</p>
     *
     * @param pool the pool to which all subtree nodes are to be recycled
     */
    public final void recycle(ObjectPool<CallTree> pool) {
        assert !(this instanceof Root);
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
        childIds = null;
        maybeChildIds = null;
        depth = 0;
        if (children.size() > INITIAL_CHILD_SIZE) {
            // the overwhelming majority of call tree nodes has either one or two children
            // don't let outliers grow all lists in the pool over time
            children = new ArrayList<>(INITIAL_CHILD_SIZE);
        } else {
            children.clear();
        }
    }

    /**
     * When a regular span is activated,
     * we want it's {@link TraceContext#getId() span.id} to be added to the call tree that represents the
     * {@linkplain CallTree.Root#topOfStack top of the stack} to ensure correct parent/child relationships via re-parenting (See also {@link Span#childIds}).
     * <p>
     * However, the {@linkplain CallTree.Root#topOfStack current top of the stack} may turn out to not be the right target.
     * Consider this example:
     * </p>
     * <pre>
     * bb
     * aa aa
     *   1  1  ← activation
     * </pre>
     * <p>
     * We would add the id of span {@code 1} to {@code b}'s {@link #maybeChildIds}.
     * But after seeing the next frame,
     * we realize the {@code b} has already ended and that we should {@link #giveMaybeChildIdsTo} from {@code b} and give it to {@code a}.
     * This logic is implemented in {@link CallTree.Root#addStackTrace}.
     * After seeing another frame of {@code a}, we know that {@code 1} is really the child of {@code a}, so we {@link #transferMaybeChildIdsToChildIds()}.
     * </p>
     *
     * @param id the child span id to add to this call tree element
     */
    public void addMaybeChildId(long id) {
        if (maybeChildIds == null) {
            maybeChildIds = new LongList();
        }
        maybeChildIds.add(id);
    }

    public void addChildId(long id) {
        if (childIds == null) {
            childIds = new LongList();
        }
        childIds.add(id);
    }

    public boolean hasChildIds() {
        return (maybeChildIds != null && maybeChildIds.getSize() > 0)
            || (childIds != null && childIds.getSize() > 0);
    }

    public void recursiveGiveChildIdsTo(CallTree giveTo) {
        for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
            children.get(i).recursiveGiveChildIdsTo(giveTo);
        }
        giveChildIdsTo(giveTo);
        giveMaybeChildIdsTo(giveTo);
    }

    void giveChildIdsTo(CallTree giveTo) {
        if (this.childIds == null) {
            return;
        }
        if (giveTo.childIds == null) {
            giveTo.childIds = this.childIds;
        } else {
            giveTo.childIds.addAll(this.childIds);
        }
        this.childIds = null;
    }


    void giveLastChildIdTo(CallTree giveTo) {
        if (childIds != null && !childIds.isEmpty()) {
            giveTo.addChildId(childIds.remove(childIds.getSize() - 1));
        }
    }

    void giveMaybeChildIdsTo(CallTree giveTo) {
        if (this.maybeChildIds == null) {
            return;
        }
        if (giveTo.maybeChildIds == null) {
            giveTo.maybeChildIds = this.maybeChildIds;
        } else {
            giveTo.maybeChildIds.addAll(this.maybeChildIds);
        }
        this.maybeChildIds = null;
    }

    public int getDepth() {
        return depth;
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
         * mostly a transaction or a span which got activated in an auxiliary thread
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
        @Nullable
        private CallTree previousTopOfStack;
        @Nullable
        private CallTree topOfStack;

        private final LongHashSet activeSet = new LongHashSet();

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
            if (topOfStack != null) {
                long spanId = TraceContext.getSpanId(active);
                activeSet.add(spanId);
                if (!isNestedActivation(topOfStack)) {
                    topOfStack.addMaybeChildId(spanId);
                }
            }
        }

        private boolean isNestedActivation(CallTree topOfStack) {
            return isAnyActive(topOfStack.childIds) || isAnyActive(topOfStack.maybeChildIds);
        }

        private boolean isAnyActive(@Nullable LongList spanIds) {
            if (spanIds == null) {
                return false;
            }
            for (int i = 0, size = spanIds.getSize(); i < size; i++) {
                if (activeSet.contains(spanIds.get(i))) {
                    return true;
                }
            }
            return false;
        }

        public void onDeactivation(byte[] deactivated, byte[] active, long timestamp) {
            if (logger.isDebugEnabled() && !Arrays.equals(activeSpanSerialized, deactivated)) {
                logger.warn("Illegal state: deactivating span that is not active");
            }
            if (activeSpan != null) {
                handleDeactivation(activeSpan, activationTimestamp, timestamp);
            }
            // else: activeSpan has not been materialized because no stack traces were added during this activation
            setActiveSpan(active, timestamp);
            // we're not interested in tracking nested activations that happen before we see the first stack trace
            // that's because isNestedActivation is only called if topOfStack != null
            // this optimizes for the case where we have no stack traces for a fast executing transaction
            if (topOfStack != null) {
                long spanId = TraceContext.getSpanId(deactivated);
                activeSet.remove(spanId);
            }
        }

        public void addStackTrace(ElasticApmTracer tracer, List<StackFrame> stackTrace, long nanoTime, ObjectPool<CallTree> callTreePool, long minDurationNs) {
            // only "materialize" trace context if there's actually an associated stack trace to the activation
            // avoids allocating a TraceContext for very short activations which have no effect on the CallTree anyway
            boolean firstFrameAfterActivation = false;
            if (activeSpan == null) {
                firstFrameAfterActivation = true;
                activeSpan = TraceContext.with64BitId(tracer);
                activeSpan.deserialize(activeSpanSerialized, rootContext.getServiceName());
            }
            previousTopOfStack = topOfStack;
            topOfStack = addFrame(stackTrace, stackTrace.size(), activeSpan, activationTimestamp, nanoTime, callTreePool, minDurationNs, this);

            // After adding the first frame after an activation, we can check if we added the child ids to the correct CallTree
            // If the new top of stack is not a successor (a different branch vs just added nodes on the same branch)
            // we have to transfer the child ids of not yet deactivated spans to the new top of the stack.
            // See also CallTreeTest.testActivationAfterMethodEnds and following tests.
            if (firstFrameAfterActivation && previousTopOfStack != topOfStack && previousTopOfStack != null && previousTopOfStack.hasChildIds()) {
                if (!topOfStack.isSuccessor(previousTopOfStack)) {
                    CallTree commonAncestor = findCommonAncestor(previousTopOfStack, topOfStack);
                    previousTopOfStack.giveMaybeChildIdsTo(commonAncestor != null ? commonAncestor : topOfStack);
                }
            }
        }

        @Nullable
        private CallTree findCommonAncestor(CallTree previousTopOfStack, CallTree topOfStack) {
            int maxDepthOfCommonAncestor = Math.min(previousTopOfStack.getDepth(), topOfStack.getDepth());
            CallTree commonAncestor = null;
            // i = 1 avoids considering the CallTree.Root node which is always the same
            for (int i = 1; i <= maxDepthOfCommonAncestor; i++) {
                CallTree ancestor1 = previousTopOfStack.getNthParent(previousTopOfStack.getDepth() - i);
                CallTree ancestor2 = topOfStack.getNthParent(topOfStack.getDepth() - i);
                if (ancestor1 == ancestor2) {
                    commonAncestor = ancestor1;
                } else {
                    break;
                }
            }
            return commonAncestor;
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
        public int spanify() {
            int createdSpans = 0;
            List<CallTree> callTrees = getChildren();
            for (int i = 0, size = callTrees.size(); i < size; i++) {
                createdSpans += callTrees.get(i).spanify(this, rootContext);
            }
            return createdSpans;
        }

        public TraceContext getRootContext() {
            return rootContext;
        }

        public long getEpochMicros(long nanoTime) {
            return rootContext.getClock().getEpochMicros(nanoTime);
        }

        /**
         * Recycles this tree to the provided pools.
         * First, all child subtrees are recycled recursively to the children pool.
         * Then, {@code this} root node is recycled to the root pool. This means that <b>the caller of this method
         * should make sure that no reference to this root object is held anywhere</b>.
         *
         * @param childrenPool object pool for all non-root nodes
         * @param rootPool     object pool for root nodes
         */
        public void recycle(ObjectPool<CallTree> childrenPool, ObjectPool<CallTree.Root> rootPool) {
            List<CallTree> children = getChildren();
            for (int i = 0, size = children.size(); i < size; i++) {
                children.get(i).recycle(childrenPool);
            }
            rootPool.recycle(this);
        }

        public void end(ObjectPool<CallTree> pool, long minDurationNs) {
            end(pool, minDurationNs, this);
        }

        @Override
        public void resetState() {
            super.resetState();
            rootContext.resetState();
            activeSpan = null;
            activationTimestamp = -1;
            Arrays.fill(activeSpanSerialized, (byte) 0);
            previousTopOfStack = null;
            topOfStack = null;
            activeSet.clear();
        }
    }
}
