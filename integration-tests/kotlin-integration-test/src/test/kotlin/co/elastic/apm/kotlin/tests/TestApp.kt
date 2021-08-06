package co.elastic.apm.kotlin.tests

import co.elastic.apm.api.ElasticApm
import co.elastic.apm.attach.ElasticApmAttacher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.system.exitProcess
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun main(args: Array<String>) {
    ElasticApmAttacher.attach()

    val visitedCoroutines = mutableSetOf<String>()
    val coroutinesResults = mutableListOf<Deferred<*>>()

    val rootTransaction = ElasticApm.startTransaction()
    rootTransaction.setName("Root Transaction")
    rootTransaction.setLabel("Label", "LabelValue")
    rootTransaction.activate()

    runBlocking(Executors.newFixedThreadPool(5).asCoroutineDispatcher()) {
        for (i in 1..5) {
            coroutinesResults.add(
                async {
                    visitedCoroutines.add("$i")
                    assertEquals(rootTransaction.id, ElasticApm.currentSpan().id)
                    delay(100L)
                    for (j in 1..10) {
                        coroutinesResults.add(
                            async {
                                visitedCoroutines.add("$i-$j")
                                assertEquals(rootTransaction.traceId, ElasticApm.currentSpan().traceId)
                                ElasticApm.currentSpan().setLabel("SpanLabel$i-$j", "SpanValue$i-$j")
                            }
                        )
                    }
                }
            )
        }
    }

    rootTransaction.end()

    assertEquals(55, coroutinesResults.size)
    runBlocking {
        coroutinesResults.forEach {
            it.await()
        }
    }

    for (i in 1..5) {
        assertTrue(visitedCoroutines.contains("$i"))
        for (j in 1..10) {
            assertTrue(visitedCoroutines.contains("$i-$j"))
        }
    }

    exitProcess(0)
}
