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
package co.elastic.apm.attach.bouncycastle;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BouncyCastleVerifierTest {

    private static final String TEST_KEY_ID = "90AD76CD56AA73A9";

    private final BouncyCastleVerifier bouncyCastleVerifier = new BouncyCastleVerifier();

    @Test
    void testValidPgpVerification() throws Exception {
        try (
            InputStream fileIS = BouncyCastleVerifierTest.class.getResourceAsStream("/test.txt");
            InputStream pgpSignatureIS = BouncyCastleVerifierTest.class.getResourceAsStream("/test.sig.asc");
            InputStream publicKeyIS = BouncyCastleVerifierTest.class.getResourceAsStream("/valid_key.asc")
        ) {
            assertThat(bouncyCastleVerifier.verifyPgpSignature(fileIS, pgpSignatureIS, publicKeyIS, TEST_KEY_ID)).isTrue();
        }
    }

    @Test
    void testInvalidSignature() throws Exception {
        try (
            InputStream fileIS = BouncyCastleVerifierTest.class.getResourceAsStream("/test.txt");
            InputStream pgpSignatureIS = BouncyCastleVerifierTest.class.getResourceAsStream("/modified-test.sig.asc");
            InputStream publicKeyIS = BouncyCastleVerifierTest.class.getResourceAsStream("/valid_key.asc")
        ) {
            assertThat(bouncyCastleVerifier.verifyPgpSignature(fileIS, pgpSignatureIS, publicKeyIS, TEST_KEY_ID)).isFalse();
        }
    }

    @Test
    void testInvalidPgpKey() throws Exception {
        try (
            InputStream fileIS = BouncyCastleVerifierTest.class.getResourceAsStream("/test.txt");
            InputStream pgpSignatureIS = BouncyCastleVerifierTest.class.getResourceAsStream("/test.sig.asc");
            InputStream publicKeyIS = BouncyCastleVerifierTest.class.getResourceAsStream("/invalid_key.asc")
        ) {
            assertThatThrownBy(() -> bouncyCastleVerifier.verifyPgpSignature(fileIS, pgpSignatureIS, publicKeyIS, TEST_KEY_ID)).isNotNull();
        }
    }
}
