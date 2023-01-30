package polling

import polling.MyApp._
import polling.TestClientAssertions.{exceptionWithCount, pollingFailure}
import polling.TestCounter.{Counter, counter}
import zio._
import zio.test._

object MyAppSpecification extends ZIOSpecDefault {
  def spec = suite("Polling logic")(
    test("Exits after 3 attempts if the client consistently returns transient errors") {
      case class TransientClient(counter: Counter) extends TestClient(counter) {
        override def submitAction =
          ZIO.fail(ClientFailure(true, "Service temporarily unavailable"))
      }
      val testClient = ZLayer.fromFunction(TransientClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(20.milliseconds).forever)
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        ClientFailure(true, "Service temporarily unavailable") -> CallCount(submit = 3, getStatus = 0, cancel = 0)
      ))
    },

    test("Exits immediately if the client returns a non-transient error") {
      case class NonTransientClient(counter: Counter) extends TestClient(counter) {
        override def submitAction =
          ZIO.fail(ClientFailure(false, "Service deleted"))
      }
      val testClient = ZLayer.fromFunction(NonTransientClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(200.millis).forever)
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        ClientFailure(false, "Service deleted") -> CallCount(submit = 1, getStatus = 0, cancel = 0)
      ))
    },

    test("Times out & cancels if the client always returns pending") {
      case class PendingClient(counter: Counter) extends TestClient(counter)
      val testClient = ZLayer.fromFunction(PendingClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(10.seconds).forever)
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        MyApp.Timeout -> CallCount(submit = 1, getStatus = 7, cancel = 1)
      ))
    }
  )
}