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
package co.elastic.apm.agent.kotlinconcurrent

import co.elastic.apm.agent.AbstractInstrumentationTest
import co.elastic.apm.agent.impl.transaction.Transaction
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class CoroutineInstrumentationTest : AbstractInstrumentationTest() {
    private lateinit var transaction: Transaction

    @BeforeEach
    fun init() {
        transaction = tracer.startRootTransaction(javaClass.classLoader)!!.withName("Transaction").activate()
    }

    @Test
    fun `Coroutine should propagate the tracing-context correctly across different threads and nested coroutines`() {
        runBlocking(Executors.newFixedThreadPool(5).asCoroutineDispatcher()) {
            for (i in 1..5) {
                async {
                    tracer.currentTransaction()!!.addCustomContext("$i", i)
                    delay(100L)
                    for (j in 1..10) {
                        async {
                            tracer.currentTransaction()!!.addCustomContext("$i-$j", i + j)
                        }
                    }
                }
            }
        }

        transaction.deactivate().end()

        for (i in 1..5) {
            assertEquals(i, reporter.transactions[0].context.getCustom("$i"))
            for (j in 1..10) {
                assertEquals(i + j, reporter.transactions[0].context.getCustom("$i-$j"))
            }
        }
    }

    @Test
    fun `Coroutine should propagate the tracing-context correctly in case of exception`() {
        val expectedException = Exception("Test")
        var actualException: Exception? = null

        val throwingI = RandomUtils.nextInt(1, 6)
        val throwingJ = RandomUtils.nextInt(1, 11)

        try {
            runBlocking(Executors.newFixedThreadPool(5).asCoroutineDispatcher()) {
                for (i in 1..5) {
                    async {
                        println("Some action")
                        delay(100L)
                        for (j in 1..10) {
                            async {
                                println("Some action")
                                if (i == throwingI && j == throwingJ) {
                                    throw expectedException
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            actualException = e
        }

        transaction.deactivate().end()

        assertEquals(expectedException, actualException)
    }

}
