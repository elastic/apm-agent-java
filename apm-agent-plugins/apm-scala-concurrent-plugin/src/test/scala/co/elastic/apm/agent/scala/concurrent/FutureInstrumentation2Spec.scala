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

class FutureInstrumentation2Spec extends FunSuite {

  private var reporter: MockReporter = _
  private var tracer: ElasticApmTracer = _
  private var coreConfiguration: CoreConfiguration = _

  override def beforeEach(context: BeforeEach): Unit = {
    reporter = new MockReporter
    val config: ConfigurationRegistry = SpyConfiguration.createSpyConfig
    coreConfiguration = config.getConfig(classOf[CoreConfiguration])
    tracer = new ElasticApmTracerBuilder().configurationRegistry(config).reporter(reporter).build
    ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install)
    tracer.start()
  }

  override def afterEach(context: AfterEach): Unit = ElasticApmAgent.reset()

  test("Scala Future should not propagate the tracing-context to unrelated threads") {
    implicit val executionContext: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

    def startFuture(num: Int): Future[(Transaction, Int)] = {
      Future {
        println(s"thread=${Thread.currentThread().getId}, trace=${tracer.currentTransaction()} before num=$num")
        val transaction = tracer.startRootTransaction(getClass.getClassLoader).withName("Transaction" + num).activate()
        println(s"thread=${Thread.currentThread().getId}, trace=${tracer.currentTransaction()} start transaction num=$num")
        Thread.sleep(10)
        (transaction, num)
      }
    }

    val futures = (1 to 10).map(x =>
      startFuture(x)
        .map { case (transaction: Transaction, num: Int) =>
          Thread.sleep(10)
          println(s"thread=${Thread.currentThread().getId}, trace=${tracer.currentTransaction()} map1 $num")
          val x = transaction == tracer.currentTransaction()
          assertEquals(transaction, tracer.currentTransaction())
          (transaction, x)
        })

    val future = Future.sequence(futures)

    val result = Await.result(future, 10.seconds).toList
    assertEquals(result.forall(x => x._2), true)
    //    result.foreach(x => x._1.deactivate().end())
    result.foreach(x => x._1.end())
  }

}
