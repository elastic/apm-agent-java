package co.elastic.apm.agent.scala.concurrent

import java.util.concurrent.Executors

import co.elastic.apm.agent.MockReporter
import co.elastic.apm.agent.bci.ElasticApmAgent
import co.elastic.apm.agent.configuration.{CoreConfiguration, SpyConfiguration}
import co.elastic.apm.agent.impl.transaction.Transaction
import co.elastic.apm.agent.impl.{ElasticApmTracer, ElasticApmTracerBuilder}
import net.bytebuddy.agent.ByteBuddyAgent
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.stagemonitor.configuration.ConfigurationRegistry

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

class FutureInstrumentationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  implicit def executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  private var reporter: MockReporter = _
  private var tracer: ElasticApmTracer = _
  private var coreConfiguration: CoreConfiguration = _
  private var transaction: Transaction = _

  override def beforeEach: Unit = {
    reporter = new MockReporter
    val config: ConfigurationRegistry = SpyConfiguration.createSpyConfig
    coreConfiguration = config.getConfig(classOf[CoreConfiguration])
    tracer = new ElasticApmTracerBuilder().configurationRegistry(config).reporter(reporter).build
    ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install)
    transaction = tracer.startRootTransaction(null).withName("Transaction").activate()
  }

  override def afterEach: Unit = {
    transaction.deactivate().end()
    ElasticApmAgent.reset()
  }

  "test" should "test" in {
    new TestFutureTraceMethods().invokeAsync()
    reporter.getTransactions should have size 1
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
      Future(methodOnWorkerThread())

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
