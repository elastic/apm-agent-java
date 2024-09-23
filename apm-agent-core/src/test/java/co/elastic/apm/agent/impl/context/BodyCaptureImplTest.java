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
package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BodyCaptureImplTest {

    @Test
    public void testAppendTruncation() {
        BodyCaptureImpl capture = new BodyCaptureImpl();
        capture.markEligibleForCapturing();
        capture.markPreconditionsPassed("foobar", 10);
        capture.startCapture();
        assertThat(capture.isFull()).isFalse();

        capture.append("123Hello World!".getBytes(StandardCharsets.UTF_8), 3, 5);
        assertThat(capture.isFull()).isFalse();

        capture.append(" from the other side".getBytes(StandardCharsets.UTF_8), 0, 20);
        assertThat(capture.isFull()).isTrue();

        ByteBuffer content = capture.getBody();
        int size = content.position();
        byte[] contentBytes = new byte[size];
        content.position(0);
        content.get(contentBytes);

        assertThat(contentBytes).isEqualTo("Hello from".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testLifecycle() {
        BodyCaptureImpl capture = new BodyCaptureImpl();

        assertThat(capture.isEligibleForCapturing()).isFalse();
        assertThat(capture.havePreconditionsBeenChecked()).isFalse();
        assertThat(capture.startCapture())
            .isFalse();
        assertThatThrownBy(() -> capture.append((byte) 42)).isInstanceOf(IllegalStateException.class);

        capture.markEligibleForCapturing();
        assertThat(capture.isEligibleForCapturing()).isTrue();
        assertThat(capture.havePreconditionsBeenChecked()).isFalse();
        assertThatThrownBy(() -> capture.append((byte) 42)).isInstanceOf(IllegalStateException.class);

        capture.markPreconditionsPassed("foobar", 42);
        assertThat(capture.isEligibleForCapturing()).isTrue();
        assertThat(capture.havePreconditionsBeenChecked()).isTrue();


        assertThat(capture.startCapture()).isTrue();
        capture.append((byte) 42); //ensure no exception thrown

        // startCapture should return true only once
        assertThat(capture.havePreconditionsBeenChecked()).isTrue();
        assertThat(capture.startCapture()).isFalse();
        assertThat(capture.havePreconditionsBeenChecked()).isTrue();

        capture.resetState();
        assertThat(capture.getCharset()).isNull();
        assertThat(capture.getBody()).isNull();
    }
}
