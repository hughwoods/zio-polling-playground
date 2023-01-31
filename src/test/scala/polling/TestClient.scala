package polling

import polling.RunStatus.{Completed, Pending}
import polling.TestClient.{runValue, successValue}
import polling.TestCounter.Counter
import zio._
import zio.test.Assertion
import zio.test.Assertion.equalTo

object ClientTesting
case class CallCount(submit: Int, getStatus: Int, cancel: Int)

object TestCounter{
  type Counter = Ref[CallCount]
  val counter = ZLayer.fromZIO(Ref.make(CallCount(0, 0, 0)))
}

abstract class TestClient(counter: Counter) extends Client {

  def submitAction: IO[ClientFailure, Run] = ZIO.succeed(runValue)

  def getStatusPendingAction: IO[ClientFailure, RunStatus] = ZIO.succeed(Pending)

  def getStatusCompletedAction: IO[ClientFailure, RunStatus] = ZIO.succeed(successValue)

  def isCompleted(counter: CallCount): Boolean = counter.getStatus > 4

  def cancelAction: IO[ClientFailure, Unit] = ZIO.unit

  final override def submit(): IO[ClientFailure, Run] =
    for {
      _ <- counter.update(prev => prev.copy(submit = prev.submit + 1))
      result <- submitAction
    } yield result

  final override def getStatus(run: Run): IO[ClientFailure, RunStatus] =
    for {
      _ <- counter.update(prev => prev.copy(getStatus = prev.getStatus + 1))
      count <- counter.get
      result <- if (isCompleted(count)) getStatusCompletedAction else getStatusPendingAction
    } yield result

  final override def cancel(run: Run): IO[ClientFailure, Unit] =
    for {
      _ <- counter.update(prev => prev.copy(cancel = prev.cancel + 1))
      result <- cancelAction
    } yield result
}

object TestClient{
  val runValue = Run("1234")

  val successValue = Completed("congratulations")

  val transientError: ClientFailure = ClientFailure(isTransient = true, message = "Service temporarily unavailable")

  val nontransientError: ClientFailure = ClientFailure(isTransient = false, message = "Service deleted")
}

object TestClientAssertions{
  def resultWithCount[A](result: A) =
    for {
      counter <- ZIO.service[Counter]
      count <- counter.get
    } yield (result, count)

  def exceptionWithCount(exception: Throwable) = resultWithCount(exception)

  def pollingFailure(expected: (Throwable, CallCount)): Assertion[Exit[(Throwable, CallCount), Any]] =
    Assertion.fails(equalTo(expected))
}
