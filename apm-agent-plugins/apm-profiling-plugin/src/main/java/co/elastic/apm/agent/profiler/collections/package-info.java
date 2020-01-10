/**
 * Copied from https://github.com/real-logic/agrona/tree/master/agrona/src/main/java/org/agrona/collections,
 * which is under Apache License 2.0.
 * <p>
 * We can't use agrona as a regular dependency as it's compiled for Java 8 and we still support Java 7.
 * That's why the relevant classes are copied over and methods referencing Java 8 types are removed.
 * </p>
 */
package co.elastic.apm.agent.profiler.collections;
