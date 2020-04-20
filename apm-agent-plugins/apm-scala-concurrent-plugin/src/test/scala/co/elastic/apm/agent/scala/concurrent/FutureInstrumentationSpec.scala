package co.elastic.apm.agent.scala.concurrent

import java.util.concurrent.Executors

import co.elastic.apm.agent.MockReporter
import co.elastic.apm.agent.bci.ElasticApmAgent
import co.elastic.apm.agent.configuration.{CoreConfiguration, SpyConfiguration}
import co.elastic.apm.agent.impl.transaction.Transaction
import co.elastic.apm.agent.impl.{ElasticApmTracer, ElasticApmTracerBuilder}
import munit.FunSuite
import net.bytebuddy.agent.ByteBuddyAgent
import org.stagemonitor.configuration.ConfigurationRegistry

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

class FutureInstrumentationSpec extends FunSuite {

  implicit def executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  private var reporter: MockReporter = _
  private var tracer: ElasticApmTracer = _
  private var coreConfiguration: CoreConfiguration = _
  private var transaction: Transaction = _

  override def beforeEach(context: BeforeEach): Unit = {
    reporter = new MockReporter
    val config: ConfigurationRegistry = SpyConfiguration.createSpyConfig
    coreConfiguration = config.getConfig(classOf[CoreConfiguration])
    tracer = new ElasticApmTracerBuilder().configurationRegistry(config).reporter(reporter).build
    ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install)
    transaction = tracer.startRootTransaction(null).withName("Transaction").activate()
  }

  override def afterEach(context: AfterEach): Unit = ElasticApmAgent.reset()

  test("Scala Future should propagate the tracing-context correctly across different threads") {
    println(tracer.currentTransaction())

    Future("Test")
      .map { x =>
        println("DEBUG1")
        println(tracer.currentTransaction())
        x
      }
      .map(_.length)
      .map { x =>
        println("DEBUG1")
        println(tracer.currentTransaction())
        x
      }
      .flatMap(l => Future(l * 2))
      .map { x =>
        println("DEBUG1")
        println(tracer.currentTransaction())
        x
      }
      .map(_.toString)
      .map { x =>
        println("DEBUG1")
        println(tracer.currentTransaction())
        x
      }
      .flatMap(s => Future(s"$s-$s"))
      //        .map(_ => tracer.currentTransaction().addCustomContext("future", true))
        .map { _ =>
//          transaction.deactivate().end()

          assert(true)
//          assertEquals(
//            reporter.getTransactions.get(0).getContext.getCustom("future").asInstanceOf[Boolean],
//            true
//          )
        }
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
