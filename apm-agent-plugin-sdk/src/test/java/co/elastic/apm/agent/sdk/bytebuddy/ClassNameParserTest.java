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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import co.elastic.apm.agent.sdk.bytebuddy.clazzes.ParentClass.InnerClass;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.ParentClass.InnerClass.NestedInnerClass;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.ParentObject.ChildScalaObject$;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.SimpleClass;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.AnonymousClass;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.AnonymousNestedClass;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.Dollar$Class;
import co.elastic.apm.agent.sdk.bytebuddy.clazzes.ScalaObject$;

class ClassNameParserTest {

    @Test
    void testSimpleClassName() {
        String className = ClassNameParser.parse(SimpleClass.class.getName());
        assertThat(className).isEqualTo("SimpleClass");
    }
    @Test
    void testNestedClassName() {
        String className = ClassNameParser.parse(InnerClass.class.getName());
        assertThat(className).isEqualTo("InnerClass");
    }
    @Test
    void testDoubleNestedClassName() {
        String className = ClassNameParser.parse(NestedInnerClass.class.getName());
        assertThat(className).isEqualTo("NestedInnerClass");
    }
    @Test
    void testDollarClassName() {
        String className = ClassNameParser.parse(Dollar$Class.class.getName());
        //We have no way to know if the class name is Dollar$Class or Class is a nested class of Dollar
        //As dollar signs should not be used in names, it should be fine to detect Class instead of Dollar$Class
        assertThat(className).describedAs("Dollar signs should not be used in names").isEqualTo("Class");
    }
    @Test
    void testAnonymousClassName() {
        String className = ClassNameParser.parse(AnonymousClass.getAnonymousClass().getName());
        assertThat(className).isEqualTo("AnonymousClass$1");
    }
    @Test
    void testAnonymousNestedClassName() {
        String className = ClassNameParser.parse(InnerClass.getAnonymousClass().getName());
        assertThat(className).isEqualTo("InnerClass$1");
    }
    @Test
    void testAnonymousInAnonymousClassName() {
        String className = ClassNameParser.parse(AnonymousNestedClass.getAnonymousClass().getName());
        assertThat(className).isEqualTo("AnonymousNestedClass$1$1");
    }
    @Test
    void testScalaObjectClassName() {
        String className = ClassNameParser.parse(ScalaObject$.class.getName());
        assertThat(className).isEqualTo("ScalaObject");
    }
    @Test
    void testNestedScalaObjectClassName() {
        String className = ClassNameParser.parse(ChildScalaObject$.class.getName());
        assertThat(className).isEqualTo("ChildScalaObject");
    }
}
