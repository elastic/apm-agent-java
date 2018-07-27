/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.benchmark;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.ReporterConfiguration;
import co.elastic.apm.util.MathUtils;
import org.ehcache.sizeof.SizeOf;

public class SizeOfSpan {

    public static void main(String[] args) {
        final SizeOf sizeOf = SizeOf.newInstance();
        final long sizeOfSpan = sizeOf.deepSizeOf(new Span(null));
        final long sizeOfTransaction = sizeOf.deepSizeOf(new Transaction(null));
        final long sizeOfError = sizeOf.deepSizeOf(new ErrorCapture());

        System.out.println("sizeof span: " + sizeOfSpan);
        System.out.println("sizeof transaction: " + sizeOfTransaction);
        System.out.println("sizeof error: " + sizeOfError);

        final int queueSize = MathUtils.getNextPowerOf2(new ReporterConfiguration().getMaxQueueSize());
        final long sizeOfObjectPools = queueSize * 2 * sizeOfSpan +
            queueSize * 2 * sizeOfTransaction +
            queueSize * sizeOfError;
        System.out.println("sizeOfObjectPools: " + sizeOfObjectPools / 1024.0 / 1024.0 + " MiB");
    }
}
