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
package co.elastic.apm.agent.jdbc.signature.filter;

import java.util.ArrayList;
import java.util.List;

public class FilterChain implements Filter {
	public static final FilterChain DEFAULT_CHAIN = new FilterChain(new JdbcFilter());
	private final List<Filter> filter=new ArrayList<>();
	public FilterChain() {}
	public FilterChain(Filter... filter) {
		for(int i=0;i<filter.length;i++) {
			this.filter.add(filter[i]);
		}
	}
	public void addFilter(Filter f) {
		filter.add(f);
	}

	@Override
	public String doFilter(String sql) {
		String current = sql;
		for(Filter f : filter) {
			current=f.doFilter(current);
		}
		return current;
	}
}
