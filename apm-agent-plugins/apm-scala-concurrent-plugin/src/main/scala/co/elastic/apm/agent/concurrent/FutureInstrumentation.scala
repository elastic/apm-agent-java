package co.elastic.apm.agent.concurrent

import java.util

import co.elastic.apm.agent.bci.ElasticApmInstrumentation
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers._

import scala.collection.JavaConverters._
import scala.util.Try

class FutureInstrumentation extends ElasticApmInstrumentation {

  override def getTypeMatcher: ElementMatcher[_ >: TypeDescription] =
    hasSuperType[TypeDescription](named("scala.concurrent.Future"))
      .or(hasSuperType(named("scala.concurrent.impl.Promise")))
      .or(hasSuperType(named("scala.concurrent.impl.Promise$Transformation")))
      .or(hasSuperType(named("scala.concurrent.Future$")))

  override def getMethodMatcher: ElementMatcher[_ >: MethodDescription] =
    named[MethodDescription]("onComplete").and(returns(classOf[Unit])).and(takesArguments(classOf[Try[_] => _]))
//    .or(named[MethodDescription]("transform").and(returns(classOf[Future[_]])).and(takesArguments(classOf[Try[_] => Try[_]])))
//    .or(named[MethodDescription]("transformWith").and(returns(classOf[Future[_]])).and(takesArguments(classOf[Try[_] => Future[_]])))
    .and(not(isTypeInitializer[MethodDescription]()))

  override def getInstrumentationGroupNames: util.Collection[String] = List("concurrent", "future").asJavaCollection

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def onComplete(@Advice.Argument(value = 0, readOnly = true) callback: Try[_] => _): Unit = {
    val active = ElasticApmInstrumentation.getActive
    val tracer = ElasticApmInstrumentation.tracer
    if (active != null && tracer != null && tracer.isWrappingAllowedOnThread) {


      active.setDiscard(false)
      tracer.avoidWrappingOnThread()
    }
  }

}
