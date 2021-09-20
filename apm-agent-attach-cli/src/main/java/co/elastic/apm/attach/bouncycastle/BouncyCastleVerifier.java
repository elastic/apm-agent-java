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

import co.elastic.apm.attach.PgpSignatureVerifier;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import java.io.InputStream;
import java.util.Locale;

/**
 * DO NOT ACCESS DIRECTLY FROM PRODUCTION CODE!
 *
 * This class should only be accessed through {@link co.elastic.apm.attach.PgpSignatureVerifierLoader} because it
 * depends on Bouncy Castle that must be loaded in the original jars through a dedicated class loader.
 * It is excluded from the CLI jar during build.
 * The only reason it is public is so we can instantiate through reflection without invoking the deprecated
 * {@code setAccessible(true)}.
 */
public class BouncyCastleVerifier implements PgpSignatureVerifier {

    /**
     * A Bouncy Castle implementation for PGP signature verification.
     *
     * {@inheritDoc}
     */
    public boolean verifyPgpSignature(InputStream toVerify, InputStream expectedPgpSignature,
                                      InputStream rawPublicKey, String keyID) throws Exception {
        // read expected signature
        final JcaPGPObjectFactory factory = new JcaPGPObjectFactory(PGPUtil.getDecoderStream(expectedPgpSignature));
        final PGPSignature signature = ((PGPSignatureList) factory.nextObject()).get(0);

        // validate the signature has key ID matching our public key ID
        final String signatureKeyID = Long.toHexString(signature.getKeyID()).toUpperCase(Locale.ROOT);
        if (!keyID.equals(signatureKeyID)) {
            throw new IllegalStateException("Key id [" + signatureKeyID + "] does not match expected key id [" + keyID + "]");
        }

        // compute the signature of the downloaded agent jar
        final PGPPublicKeyRingCollection collection = new PGPPublicKeyRingCollection(new ArmoredInputStream(rawPublicKey), new JcaKeyFingerprintCalculator());
        final PGPPublicKey key = collection.getPublicKey(signature.getKeyID());
        signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(new BouncyCastleFipsProvider()), key);
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = toVerify.read(buffer)) != -1) {
            signature.update(buffer, 0, read);
        }

        // verify calculated signature of the downloaded jar with the expected signature from maven
        return signature.verify();
    }
}
