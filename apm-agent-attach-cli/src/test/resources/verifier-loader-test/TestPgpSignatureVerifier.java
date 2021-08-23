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
package co.elastic.apm.attach.clitest;

import co.elastic.apm.attach.PgpSignatureVerifier;

import java.io.InputStream;

/**
 * This class is only used for testing the {@link co.elastic.apm.attach.PgpSignatureVerifierLoader}.
 * It cannot be in the classpath, so to ensure that it is loaded by the dedicated loader.
 * In order to resolve properly, it must have the {@code ExternalDependency} availale alongside it.
 * Both classes are compiled and packaged into jars outside of this project and are located in the test lib dir.
 */
public class TestPgpSignatureVerifier implements PgpSignatureVerifier {

    @Override
    public boolean verifyPgpSignature(InputStream verifiedFileIS, InputStream expectedPgpSignatureIS, InputStream rawPublicKeyIS, String keyID) throws Exception {
        ExternalDependency externalDependency = new ExternalDependency();
        externalDependency.someMethod();
        return true;
    }
}
