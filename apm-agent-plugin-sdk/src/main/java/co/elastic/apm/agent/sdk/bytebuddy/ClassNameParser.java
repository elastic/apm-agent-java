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
package co.elastic.apm.agent.sdk.bytebuddy;

/**
 * Utility class to extract the correct simple class name
 */
public class ClassNameParser {
    /**
     * Utility class, do not instantiate
     */
    private ClassNameParser() {}
    /**
     * returns true if the string only contains digits
     * @param str the string to check
     * @return
     */
    private static boolean isNumeric(String str) {
    	for(int i = 0; i< str.length(); i++) {
    		if(!Character.isDigit(str.charAt(i))) {
        		return false;
    		}
    	}
    	return true;
    }
    /**
     * this method parses the anonymous class name from the full class name
     * @param className
     * @return
     */
    private static String getAnonymousClassName(String className) {
    	int lastDollarIndex = className.lastIndexOf('$');
    	int currentDollarIndex;
    	String nestedClassName;
    	// we do not want to show just a number for anonymous classes, so we walk back until we find a named (normal or nested) class
    	do {
    		currentDollarIndex = className.lastIndexOf('$', lastDollarIndex -1);
    		nestedClassName = className.substring(currentDollarIndex + 1, lastDollarIndex);
    		lastDollarIndex = currentDollarIndex;
    	} while(currentDollarIndex != -1 && isNumeric(nestedClassName));
        return className.substring(currentDollarIndex + 1);
    }
    /**
     * Parses the class name from nested and anonymous classes (containing a $ sign)
     * <p>
     * Note: We cannot know if the $ sign is part of the class name or denotes a nested/anonymous class. As $ signs should not be used in class names anyway,
     *       we handle them always as a class separator
     * </p>
     * @param className
     * @return
     */
    private static String getNestedClassName(String className) {
        int dollarIndex = className.lastIndexOf('$');
        String innerClassName = className.substring(dollarIndex + 1);
        if(isNumeric(innerClassName)) {
            // this is an anonymous inner class
        	className = getAnonymousClassName(className);
        } else {
            className = innerClassName;
        }
        return className;
    }
    /**
     * Parses the simple class name from a class name
     * @param className
     * @return
     */
    public static String parse(String className) {
        //remove package name
        className = className.substring(className.lastIndexOf('.') + 1);
        if(className.contains("$")) {
            if(className.endsWith("$")) {
                // this can happen if the source code was in Scala and the object keyword was used
                // https://www.toptal.com/scala/scala-bytecode-and-the-jvm
                className = className.substring(0, className.length() - 1);
                if(className.contains("$"))
                {
                	className = getNestedClassName(className);
                }
            } else {
                className = getNestedClassName(className);
            }
        }
        return className;
    }
}
