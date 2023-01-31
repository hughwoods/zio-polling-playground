package polling

import polling.MyApp._
import polling.TestClient.{nontransientError, successValue, transientError}
import polling.TestClientAssertions.{exceptionWithCount, pollingFailure, resultWithCount}
import polling.TestCounter.{Counter, counter}
import zio._
import zio.test._

object MyAppSpecification extends ZIOSpecDefault {
  def spec = suite("Polling logic")(
    test("Exits after 3 attempts if submit consistently returns transient errors") {
      case class TransientClient(counter: Counter) extends TestClient(counter) {
        override def submitAction = ZIO.fail(transientError)
      }
      val testClient = ZLayer.fromFunction(TransientClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(2.seconds))
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        transientError, CallCount(submit = 3, getStatus = 0, cancel = 0)
      ))
    },

    test("Exits immediately if submit returns a non-transient error") {
      case class NonTransientClient(counter: Counter) extends TestClient(counter) {
        override def submitAction = ZIO.fail(nontransientError)
      }
      val testClient = ZLayer.fromFunction(NonTransientClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(1.seconds))
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        nontransientError, CallCount(submit = 1, getStatus = 0, cancel = 0)
      ))
    },

    test("Cancels & exits if getStatus repeatedly returns a transient error") {
      case class TransientClient(counter: Counter) extends TestClient(counter) {
        override def getStatusPendingAction =
          ZIO.fail(ClientFailure(true, "Service temporarily unavailable"))
      }
      val testClient = ZLayer.fromFunction(TransientClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(5.seconds))
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        transientError, CallCount(submit = 1, getStatus = 3, cancel = 1)
      ))
    },

    test("Cancels & exits if getStatus returns a non-transient error") {
      case class NonTransientClient(counter: Counter) extends TestClient(counter) {
        override def getStatusPendingAction = ZIO.fail(nontransientError)
      }
      val testClient = ZLayer.fromFunction(NonTransientClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(3.seconds))
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        nontransientError, CallCount(submit = 1, getStatus = 1, cancel = 1)
      ))
    },

    test("Times out & cancels if the client always returns pending") {
      case class PendingClient(counter: Counter) extends TestClient(counter) {
        override def isCompleted(counter: CallCount): Boolean = false
      }
      val testClient = ZLayer.fromFunction(PendingClient.apply _)

      val result = pollingLogic
        .raceFirst(TestClock.adjust(60.seconds))
        .flatMapError(exceptionWithCount)
        .provide(testClient, counter)

      assertZIO(result.exit)(pollingFailure(
        MyApp.Timeout, CallCount(submit = 1, getStatus = 7, cancel = 1)
      ))
    },

    test("Returns value if all interactions with the client are successful") {
      case class CompletedClient(counter: Counter) extends TestClient(counter) {
        override def isCompleted(counter: CallCount): Boolean = true
      }
      val testClient = ZLayer.fromFunction(CompletedClient.apply _)

      val testRun =
        for {
          poll <- pollingLogic
          clock <- TestClock.adjust(6.seconds).fork
          _ <- clock.join
          testOutput <- resultWithCount(poll)
        } yield assertTrue(testOutput == (successValue, CallCount(1, 1, 0)))

      testRun.provide(testClient, counter)
    }
  )
}