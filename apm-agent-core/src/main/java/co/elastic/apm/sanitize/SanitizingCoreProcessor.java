/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.sanitize;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;

/**
 * Sanitizes common fields,
 * which are not specific to a certain plugin,
 * according to the {@link CoreConfiguration#sanitizeFieldNames} setting
 */
public class SanitizingCoreProcessor extends AbstractSanitizingProcessor {

    @Override
    public void processBeforeReport(Transaction transaction) {
        sanitizeContext(transaction.getContext());
    }

    @Override
    public void processBeforeReport(ErrorCapture error) {
        sanitizeContext(error.getContext());
    }

    private void sanitizeContext(Context context) {
        sanitizeMap(context.getTags());
        sanitizeMap(context.getCustom());
    }
}
