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
package co.elastic.apm.attach;

import java.io.InputStream;

public interface PgpSignatureVerifier {

    /**
     * Verifying the given file's PGP signature based on the given public key ID and the expected signature.
     *
     * @param toVerify the file to verify
     * @param expectedPgpSignature the expected PGP signature, based on the public key corresponding the given key ID
     * @param rawPublicKey PGP public key
     * @param keyID PGP public key ID corresponding the {@code publicKeyIS} argument
     * @return {@code true} if the provided file was verified successfully, {@code false} otherwise
     * @throws Exception indication failure to read from any of the given {@link InputStream}s or failure during
     * the execution of PGP verification
     */
    boolean verifyPgpSignature(InputStream toVerify, InputStream expectedPgpSignature,
                               InputStream rawPublicKey, String keyID) throws Exception;
}
