package co.elastic.apm.agent.scalaconcurrent

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
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success}

class FutureInstrumentationSpec extends FunSuite {

  private var reporter: MockReporter = _
  private var tracer: ElasticApmTracer = _
  private var coreConfiguration: CoreConfiguration = _
  private var transaction: Transaction = _

  override def beforeEach(context: BeforeEach): Unit = {
    reporter = new MockReporter
    val config: ConfigurationRegistry = SpyConfiguratiEndpointMessageListeneron.createSpyConfig
    coreConfiguration = config.getConfig(classOf[CoreConfiguration])
    tracer = new ElasticApmTracerBuilder().configurationRegistry(config).reporter(reporter).build
    ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install)
    tracer.start()
    transaction = tracer.startRootTransaction(getClass.getClassLoader).withName("Transaction").activate()
  }

  override def afterEach(context: AfterEach): Unit = ElasticApmAgent.reset()

  test("Scala Future should propagate the tracing-context correctly across different threads") {
    implicit val executionContext: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

    val future =  Future("Test")
      .map(_.length)
      .flatMap(l => Future(l * 2))
      .map(_.toString)
      .flatMap(s => Future(s"$s-$s"))
      .map(_ => tracer.currentTransaction().addCustomContext("future", true))

    Await.ready(future, 10.seconds)
    transaction.deactivate().end()
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future").asInstanceOf[Boolean],
      true
    )


  }

  test("Worker thread should correctly set context on the current transaction") {
    implicit val executionContext: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

    new TestFutureTraceMethods().invokeAsync(tracer)
    transaction.deactivate().end()
    assertEquals(reporter.getTransactions.size(), 1)
    assertEquals(reporter.getSpans.size(), 0)
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future").asInstanceOf[Boolean],
      true
    )
  }

  test("Multiple async operations should be able to set context on the current transaction") {

    implicit val multiPoolEc: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

    val future = Future
      .traverse(1 to 100) { _ =>
        Future.sequence(List(
          Future {
            Thread.sleep(25)
            tracer.currentTransaction().addCustomContext("future1", true)
          },
          Future {
            Thread.sleep(50)
            tracer.currentTransaction().addCustomContext("future2", true)
          },
          Future {
            Thread.sleep(10)
            tracer.currentTransaction().addCustomContext("future3", true)
          }
        ))
      }

    Await.ready(future, 10.seconds)
    transaction.deactivate().end()
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future1").asInstanceOf[Boolean],
      true
    )
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future2").asInstanceOf[Boolean],
      true
    )
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future3").asInstanceOf[Boolean],
      true
    )

  }

  test("Handle a combination of Promises and Futures correctly") {

    implicit val multiPoolEc: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

    val promise = Promise[Int]()

    Future { Thread.sleep(100) }
      .map(_ => 42)
      .onComplete {
        case Success(value) => promise.success(value)
        case Failure(exception) => promise.failure(exception)
      }

    val future = promise
      .future
      .map(_ => tracer.currentTransaction().addCustomContext("future", true))

    Await.ready(future, 10.seconds)
    transaction.deactivate().end()
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future").asInstanceOf[Boolean],
      true
    )

  }

  test("Handle a Future.sequence correctly") {

    implicit val multiPoolEc: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

    val future = Future
      .sequence(List(
        Future(Thread.sleep(25))
      ))
      .map(_ => tracer.currentTransaction().addCustomContext("future", true))

    Await.ready(future, 10.seconds)
    transaction.deactivate().end()
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future").asInstanceOf[Boolean],
      true
    )

  }

  test("Handle a combination of Promises and complex Futures correctly") {

    implicit val multiPoolEc: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

    val promise = Promise[Int]()

      Future
        .sequence(List(
          Future(Thread.sleep(25))
        ))
        .map(_ => tracer.currentTransaction().addCustomContext("future1", true))
        .map(_ => 42)
        .onComplete {
          case Success(value) => promise.success(value)
          case Failure(exception) => promise.failure(exception)
      }

    val future = promise
      .future
      .map(_ => tracer.currentTransaction().addCustomContext("future2", true))

    Await.ready(future, 10.seconds)
    transaction.deactivate().end()
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future1").asInstanceOf[Boolean],
      true
    )
    assertEquals(
      reporter.getTransactions.get(0).getContext.getCustom("future2").asInstanceOf[Boolean],
      true
    )

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
    def invokeAsync(tracer: ElasticApmTracer)(implicit ec: ExecutionContext): Unit = blockingMethodOnMainThread(tracer)

    private def blockingMethodOnMainThread(tracer: ElasticApmTracer)(implicit ec: ExecutionContext): Unit = {
      try {
        Await.result(nonBlockingMethodOnMainThread(tracer), 10.seconds)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }

    private def nonBlockingMethodOnMainThread(tracer: ElasticApmTracer)(implicit ec: ExecutionContext): Future[Unit] =
      Future(methodOnWorkerThread(tracer))

    private def methodOnWorkerThread(tracer: ElasticApmTracer): Unit = longMethod(tracer)

    private def longMethod(tracer: ElasticApmTracer): Unit = {
      try {
        Thread.sleep(100)
        tracer.currentTransaction().addCustomContext("future", true)
      } catch {
        case e: InterruptedException => e.printStackTrace()
      }
    }

}
