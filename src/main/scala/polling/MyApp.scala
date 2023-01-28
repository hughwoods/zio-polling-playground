package polling

import polling.RunStatus.Completed
import zio.Console.printLine
import zio.{RIO, Schedule, ZIO, ZIOAppDefault, durationInt}

import scala.concurrent.TimeoutException

object MyApp extends ZIOAppDefault {
  val transientRetryPolicy =
    Schedule.recurWhile[ClientFailure](_.isTransient) &&
      Schedule.spaced(100.millis).jittered &&
      Schedule.recurs(2)

  val pollingSchedule =
    Schedule.recurUntil[RunStatus, Completed] { case c: Completed => c } &&
      Schedule.fixed(2.seconds).unit

  val pollingLogic: RIO[Client, Completed] = {
    for {
      client <- ZIO.service[Client]
      run <- client.submit().retry(transientRetryPolicy)
      check = client.getStatus(run).retry(transientRetryPolicy)
      result <- check.repeat(pollingSchedule).timeoutFail(new TimeoutException)(1.minutes)
        .tapError(_ => client.cancel(run).retry(transientRetryPolicy))
    } yield result.get
  }

  override def run = pollingLogic
    .flatMap(c => printLine(c.result))
    .provide(TestClient.layer)
}
