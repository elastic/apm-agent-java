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

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class CallTree {

    @Nullable
    private CallTree parent;
    protected int count;
    private List<CallTree> children = new ArrayList<>();
    private StackFrame frame;
    protected long start;
    private long lastSeen;
    private boolean ended;
    @Nullable
    private TraceContext traceContext;
    private boolean isSpan;

    public CallTree(@Nullable CallTree parent, StackFrame frame, @Nullable TraceContext traceContext, long nanoTime) {
        this.parent = parent;
        this.frame = frame;
        this.start = nanoTime;
        this.traceContext = traceContext;
    }

    public static CallTree.Root createRoot(TraceContext traceContext, long nanoTime) {
        return new CallTree.Root(traceContext, nanoTime);
    }

    protected void addFrame(ListIterator<StackFrame> iterator, @Nullable TraceContext traceContext, long nanoTime) {
        count++;
        lastSeen = nanoTime;
        //     c ee   <- traceContext not set - they are not a child of the active span but the frame below them
        //   bbb dd   <- traceContext set
        //   ------   <- all new CallTree during this period should have the traceContext set
        // a aaaaaa a
        //  |      |
        // active  deactive

        // this branch is already aware of the activation
        if (Objects.equals(this.traceContext, traceContext)) {
            traceContext = null;
        }

        CallTree lastChild = getLastChild();
        // if the frame corresponding to the last child is not in the stack trace
        // it's assumed to have ended one tick ago
        boolean endChild = true;
        if (iterator.hasPrevious()) {
            final StackFrame frame = iterator.previous();
            if (lastChild != null) {
                if (!lastChild.isEnded() && lastChild.frame.equals(frame)) {
                    lastChild.addFrame(iterator, traceContext, nanoTime);
                    endChild = false;
                } else {
                    // we're looking at a frame which is a new sibling to listChild
                    lastChild.end();
                    addChild(frame, iterator, traceContext, nanoTime);
                }
            } else {
                addChild(frame, iterator, traceContext, nanoTime);
            }
        }
        if (lastChild != null && !lastChild.isEnded() && endChild) {
            lastChild.end();
        }
    }

    void addChild(StackFrame frame, ListIterator<StackFrame> iterator, @Nullable TraceContext traceContext, long nanoTime) {
        CallTree callTree = new CallTree(this, frame, traceContext, nanoTime);
        children.add(callTree);
        callTree.addFrame(iterator, null, nanoTime);
    }

    long getDurationUs() {
        return (lastSeen - start) / 1000;
    }

    public int getCount() {
        return count;
    }

    public StackFrame getFrame() {
        return frame;
    }

    public List<CallTree> getChildren() {
        return children;
    }

    void end() {
        if (isEnded()) {
            return;
        }
        ended = true;
        CallTree lastChild = getLastChild();
        if (lastChild != null && !lastChild.isEnded()) {
            lastChild.end();
        }
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

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

    public void toString(Appendable out) throws IOException {
        toString(out, 0);
    }

    private void toString(Appendable out, int level) throws IOException {
        for (int i = 0; i < level; i++) {
            out.append("  ");
        }
        out.append(frame.getClassName())
            .append('.')
            .append(frame.getMethodName())
            .append(' ').append(Integer.toString(count))
            .append('\n');
        for (CallTree node : children) {
            node.toString(out, level + 1);
        }
    }

    void spanify(CallTree.Root root, TraceContext parentContext) {
        if (traceContext != null) {
            parentContext = traceContext;
        }
        Span span = null;
        if (!isPillar() || isLeaf()) {
            span = asSpan(root, parentContext);
            this.isSpan = true;
        }
        for (CallTree child : getChildren()) {
            child.spanify(root, span != null ? span.getTraceContext() : parentContext);
        }
        if (span != null) {
            span.end(span.getTimestamp() + getDurationUs());
        }
    }

    protected Span asSpan(Root root, TraceContext parentContext) {
        Span span = parentContext.createSpan(root.getStartTimestampUs(this))
            .withType("app")
            .withSubtype("inferred");

        frame.appendSimpleClassName(span.getNameForSerialization());
        span.appendToName("#");
        span.appendToName(frame.getMethodName());

        // we're not interested in the very bottom of the stack which contains things like accepting and handling connections
        if (root.traceContext != parentContext) {
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

    public void removeNodesFasterThan(float percent, int minCount) {
        int ticks = (int) (count * percent);
        removeNodesFasterThan(Math.max(ticks, minCount));
    }

    public void removeNodesFasterThan(int minCount) {
        for (Iterator<CallTree> iterator = getChildren().iterator(); iterator.hasNext(); ) {
            CallTree child = iterator.next();
            if (child.count < minCount) {
                iterator.remove();
            } else {
                child.removeNodesFasterThan(minCount);
            }
        }
    }

    public static class Root extends CallTree {
        private static final StackFrame ROOT_FRAME = new StackFrame("root", "root");
        private long timestampUs;
        protected TraceContext traceContext;
        private TraceContext activeSpan;

        public Root(TraceContext traceContext, long nanoTime) {
            super(null, ROOT_FRAME, traceContext, nanoTime);
            this.traceContext = traceContext;
            activeSpan = traceContext;
        }

        public void setActiveSpan(TraceContext context) {
            this.activeSpan = context;
        }

        public void addStackTrace(List<StackFrame> stackTrace, long nanoTime) {
            if (count == 0) {
                timestampUs = this.traceContext.getClock().getEpochMicros();
            }
            addFrame(stackTrace.listIterator(stackTrace.size()), activeSpan, nanoTime);
        }

        public void spanify() {
            for (CallTree child : getChildren()) {
                child.spanify(this, traceContext);
            }
        }

        public TraceContext getTraceContext() {
            return traceContext;
        }

        public long getTimestampUs() {
            return timestampUs;
        }

        public long getStartTimestampUs(CallTree callTree) {
            long offsetUs = (callTree.start - this.start) / 1000;
            return offsetUs + timestampUs;
        }
    }
}
