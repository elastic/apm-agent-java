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

public class JdbcFilter implements Filter {
	private static final ThreadLocal<StringBuilder> cache=new ThreadLocal<StringBuilder>() {
	        @Override protected StringBuilder initialValue() {
	            return new StringBuilder();
	    }
	};
	private int findFirstNonWhitespace(String str, int pos) {
		for(int i = pos+1; i < str.length(); i++){
	        if(!Character.isWhitespace(str.charAt(i))){
	        	return i;
	        }
	    }
		return -1;
	}
	@Override
	public String doFilter(String sql) {
		if(!sql.contains("{")) {
			return sql;
		}
		StringBuilder sb = cache.get();
		sb.setLength(0);
		boolean inQuote = false, inJdbcEscape = false;
		char c;
		int i = 0,j = 0;
		while(i<sql.length()) {
			c = sql.charAt(i);
			switch(c) {
				case '{':
					if(inQuote) {
						sb.append(c);
					}else {
						inJdbcEscape = true;
					}
					j=findFirstNonWhitespace(sql, i);
					if(j>i) {
						i=j;
					}
					if(sql.toLowerCase().indexOf("oj", i) == i) {
						i+=2;
					}
					break;
				case '}':
					if(inQuote) {
						sb.append(c);
					}else {
						inJdbcEscape = false;
					}
					i++;
					break;
				case '?':
				case '=':
					if(inQuote || !inJdbcEscape) {
						sb.append(c);
					}
					i++;
					break;
				case '\'':
					sb.append(c);
					inQuote = !inQuote;
					i++;
					break;
				default:
					sb.append(c);
					i++;
					break;
			}
		}
		return sb.toString();
	}
}
