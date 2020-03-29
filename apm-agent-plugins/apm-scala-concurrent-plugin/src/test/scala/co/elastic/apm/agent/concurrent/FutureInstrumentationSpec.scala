package co.elastic.apm.agent.concurrent

import java.util.concurrent.Executors

import co.elastic.apm.agent.AbstractInstrumentationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FutureInstrumentationSpec extends AbstractInstrumentationTest {

  @Test
  def testWithDefaultConfig(): Unit = {
    new TestFutureTraceMethods().invokeAsync()
    assertThat(AbstractInstrumentationTest.reporter.getTransactions().toArray).hasSize(1)
    assertThat(AbstractInstrumentationTest.reporter.getSpans().toArray).hasSize(4)
  }

  private class TestFutureTraceMethods {

    /**
     * Calling this method results in this method call tree:
     *
     *                      main thread                         |           worker thread
     * -------------------------------------------------------------------------------------------
     * invokeAsync                                              |
     *      |                                                   |
     *      --- blockingMethodOnMainThread                      |
     *                     |                                    |
     *                     --- nonBlockingMethodOnMainThread    |
     *                                      |                   |
     *                                      --------------------------> methodOnWorkerThread
     *                                                          |                |
     *                                                          |                --- longMethod
     *                                                          |
     */
    def invokeAsync(): Unit = blockingMethodOnMainThread()

    private def blockingMethodOnMainThread(): Unit = {
      try {
        Await.result(nonBlockingMethodOnMainThread(), 10.seconds)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }

    private def nonBlockingMethodOnMainThread(): Future[Unit] =
      Future(methodOnWorkerThread())(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)))

    private def methodOnWorkerThread(): Unit = longMethod()

    private def longMethod(): Unit = {
      try {
        Thread.sleep(100)
      } catch {
        case e: InterruptedException => e.printStackTrace()
      }
    }

  }

}
