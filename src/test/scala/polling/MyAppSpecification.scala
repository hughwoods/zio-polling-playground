package polling

import polling.MyApp._
import polling.TestClient.{nontransientError, successValue, transientError}
import polling.TestCounter.{CallLogger, testCallLogger}
import zio._
import zio.test._

object MyAppSpecification extends ZIOSpecDefault {
  def spec = suite("Polling logic")(
    test("Exits after 3 attempts if submit consistently returns transient errors") {
      case class TransientClient(counter: CallLogger) extends TestClient(counter) {
        override def submitAction = ZIO.fail(transientError)
      }

      val testClient = TestClient.layer(TransientClient.apply)

      val assertBehaviour: URIO[TransientClient, TestResult] = for {
        pollingFiber <- pollingLogic.fork
        _ <- TestClock.adjust(120.seconds)
        result <- pollingFiber.join.exit
        counter <- ZIO.service[TransientClient].map(_.counter)
        finalCount <- counter.get
      } yield assertTrue(
        result.causeOption.get.failureOption.get == transientError,
        finalCount.count == CallCount(submit = 3, getStatus = 0, cancel = 0)
      )

      assertBehaviour.provide(testClient)
    },

    test("Exits immediately if submit returns a non-transient error") {
      case class NonTransientClient(callLogger: CallLogger) extends TestClient(callLogger) {
        override def submitAction = ZIO.fail(nontransientError)
      }
      val testClient = ZLayer.fromFunction(NonTransientClient.apply _)

      for {
        pollingFiber <- pollingLogic.provideLayer(testClient).fork
        _ <- TestClock.adjust(120.seconds)
        result <- pollingFiber.join.exit
        callLog <- ZIO.service[CallLogger].flatMap(_.get)
      } yield assertTrue(
        result.causeOption.get.failureOption.get == nontransientError,
        callLog.count == CallCount(submit = 1, getStatus = 0, cancel = 0)
      )
    }.provide(testCallLogger),

    test("Cancels & exits if getStatus repeatedly returns a transient error") {
      case class TransientClient(callLogger: CallLogger) extends TestClient(callLogger) {
        override def getStatusPendingAction =
          ZIO.fail(ClientFailure(true, "Service temporarily unavailable"))
      }
      val testClient = ZLayer.fromFunction(TransientClient.apply _)

      for {
        pollingFiber <- pollingLogic.provideLayer(testClient).fork
        _ <- TestClock.adjust(120.seconds)
        result <- pollingFiber.join.exit
        callLog <- ZIO.service[CallLogger].flatMap(_.get)
      } yield assertTrue(
        result.causeOption.get.failureOption.get == transientError,
        callLog.count == CallCount(submit = 1, getStatus = 3, cancel = 1),
        callLog.span >= Duration.fromMillis(1000), // 500ms retry * 2
        callLog.span <= Duration.fromMillis(1200) // (500ms retry + 20% jitter) * 2
      )
    }.provide(testCallLogger),

    test("Cancels & exits if getStatus returns a non-transient error") {
      case class NonTransientClient(callLogger: CallLogger) extends TestClient(callLogger) {
        override def getStatusPendingAction = ZIO.fail(nontransientError)
      }
      val testClient = ZLayer.fromFunction(NonTransientClient.apply _)

      for {
        pollingFiber <- pollingLogic.provideLayer(testClient).fork
        _ <- TestClock.adjust(120.seconds)
        result <- pollingFiber.join.exit
        callLog <- ZIO.service[CallLogger].flatMap(_.get)
      } yield assertTrue(
        result.causeOption.get.failureOption.get == nontransientError,
        callLog.count == CallCount(submit = 1, getStatus = 1, cancel = 1)
      )
    }.provide(testCallLogger),

    test("Times out & cancels after 1 minute if the client always returns pending") {
      case class PendingClient(callLogger: CallLogger) extends TestClient(callLogger) {
        override def isCompleted(counter: CallLog): Boolean = false
      }
      val testClient = ZLayer.fromFunction(PendingClient.apply _)

      for {
        pollingFiber <- pollingLogic.provideLayer(testClient).fork
        _ <- TestClock.adjust(120.seconds)
        result <- pollingFiber.join.exit
        counter <- ZIO.service[CallLogger].flatMap(_.get)
      } yield assertTrue(
//        result.causeOption.get.failureOption.get.isInstanceOf[PollingTimeout],
        counter.count == CallCount(submit = 1, getStatus = 7, cancel = 1),
        counter.span == Duration.fromSeconds(60)
      )
    }.provide(testCallLogger),

    test("Returns value if all interactions with the client are successful") {
      case class CompletedClient(callLogger: CallLogger) extends TestClient(callLogger) {
        override def isCompleted(counter: CallLog): Boolean = true
      }
      val testClient = ZLayer.fromFunction(CompletedClient.apply _)

      for {
        pollingFiber <- pollingLogic.provideLayer(testClient).fork
        _ <- TestClock.adjust(120.seconds)
        result <- pollingFiber.join
        counter <- ZIO.service[CallLogger].flatMap(_.get)
      } yield assertTrue(
        result == successValue,
        counter.count == CallCount(1, 1, 0)
      )
    }.provide(testCallLogger)
  )
}