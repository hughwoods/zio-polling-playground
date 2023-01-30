package polling

import polling.RunStatus.Pending
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

  def submitAction: IO[ClientFailure, Run] = ZIO.succeed(Run("1234"))

  def getStatusAction: IO[ClientFailure, RunStatus] = ZIO.succeed(Pending)

  def cancelAction: IO[ClientFailure, Unit] = ZIO.unit

  final override def submit(): IO[ClientFailure, Run] =
    for {
      _ <- counter.update(prev => prev.copy(submit = prev.submit + 1))
      result <- submitAction
    } yield result

  final override def getStatus(run: Run): IO[ClientFailure, RunStatus] =
    for {
      _ <- counter.update(prev => prev.copy(getStatus = prev.getStatus + 1))
      result <- getStatusAction
    } yield result

  final override def cancel(run: Run): IO[ClientFailure, Unit] =
    for {
      _ <- counter.update(prev => prev.copy(cancel = prev.cancel + 1))
      result <- cancelAction
    } yield result
}

object TestClientAssertions{
  def exceptionWithCount(exception: Throwable) =
    for {
      counter <- ZIO.service[Counter]
      count <- counter.get
    } yield (exception, count)

  def pollingFailure(expected: (Throwable, CallCount)): Assertion[Exit[(Throwable, CallCount), Any]] =
    Assertion.fails(equalTo(expected))
}
