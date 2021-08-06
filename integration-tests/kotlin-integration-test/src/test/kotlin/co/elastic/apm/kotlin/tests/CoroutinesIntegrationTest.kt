package co.elastic.apm.kotlin.tests

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CoroutinesIntegrationTest {

    @Test
    fun `Test context propagation in Kotlin App with coroutines`() {
        assertEquals(0, KotlinRunner.exec(Class.forName("co.elastic.apm.kotlin.tests.TestAppKt")))
    }
}
