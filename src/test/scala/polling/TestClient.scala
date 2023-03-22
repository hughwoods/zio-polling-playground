package polling

import polling.RunStatus.{Completed, Pending}
import polling.TestClient.{runValue, successValue}
import polling.TestCounter.CallLogger
import zio._
import zio.test.Assertion
import zio.test.Assertion.equalTo

import java.time.Instant

object ClientTesting
case class CallCount(submit: Int, getStatus: Int, cancel: Int)

case class CallLog(submit: Seq[Instant], getStatus: Seq[Instant], cancel: Seq[Instant]){
  lazy val count = CallCount(submit.length, getStatus.length, cancel.length)
  lazy val first = Seq(submit.min, getStatus.min, cancel.min).min
  lazy val last = Seq(submit.max, getStatus.max, cancel.max).max
  lazy val span = Duration.fromInterval(first, last)
}

object TestCounter{
  type CallLogger = Ref[CallLog]
  val testCallLogger = ZLayer.fromZIO(Ref.make(CallLog(Nil, Nil, Nil)))
}

abstract class TestClient(counter: CallLogger) extends Client {

  def submitAction: IO[ClientFailure, Run] = ZIO.succeed(runValue)

  def getStatusPendingAction: IO[ClientFailure, RunStatus] = ZIO.succeed(Pending)

  def getStatusCompletedAction: IO[ClientFailure, RunStatus] = ZIO.succeed(successValue)

  def isCompleted(counter: CallLog): Boolean = counter.getStatus.length > 4

  def cancelAction: IO[ClientFailure, Unit] = ZIO.unit

  final override def submit(): IO[ClientFailure, Run] =
    for {
      callTime <- Clock.instant
      _ <- counter.update(prev => prev.copy(submit = prev.submit :+ callTime))
      result <- submitAction
    } yield result

  final override def getStatus(run: Run): IO[ClientFailure, RunStatus] =
    for {
      callTime <- Clock.instant
      _ <- counter.update(prev => prev.copy(getStatus = prev.getStatus :+ callTime))
      count <- counter.get
      result <- if (isCompleted(count)) getStatusCompletedAction else getStatusPendingAction
    } yield result

  final override def cancel(run: Run): IO[ClientFailure, Unit] =
    for {
      callTime <- Clock.instant
      _ <- counter.update(prev => prev.copy(cancel = prev.cancel :+ callTime))
      result <- cancelAction
    } yield result
}

object TestClient{
  val runValue = Run("1234")

  val successValue = Completed("congratulations")

  val transientError: ClientFailure = ClientFailure(isTransient = true, message = "Service temporarily unavailable")

  val nontransientError: ClientFailure = ClientFailure(isTransient = false, message = "Service deleted")
}
