package co.elastic.apm.agent.scalaconcurrent

import munit.{GenericAfterEach, GenericBeforeEach}

case class Rerun(maxReruns: Int) extends munit.Tag("Rerun")
class RerunSuite extends munit.FunSuite {

  def retryFutureOnException(test: Test, maxReruns: Int, n: Int = 0): TestValue = {
    val result =
      if (n == maxReruns - 1) test.body()
      else {
        test.body().recoverWith {
          case failedTest: munit.ComparisonFailException => throw failedTest
          case _ =>
            this.afterEach(new GenericAfterEach(test))
            this.beforeEach(new GenericBeforeEach(test))
            retryFutureOnException(test, maxReruns, n + 1)
        }(munitExecutionContext)
      }

    Thread.sleep(100)

    result
  }

  override def munitTestTransforms: List[TestTransform] = super.munitTestTransforms ++ List(
    new TestTransform("Rerun", { test =>
      val maxReruns = test.tags
        .collectFirst { case Rerun(maxReruns) => maxReruns }
        .getOrElse(1)
      if (maxReruns == 1) test
      else {
        test.withBody(() => {
          retryFutureOnException(test, maxReruns)
        })
      }
    })
  )
}
