package polling

import polling.RunStatus.Completed
import zio.Console.printLine
import zio.Schedule.WithState
import zio.{RIO, Schedule, ZIO, ZIOAppDefault, durationInt}

object MyApp extends ZIOAppDefault {

  val transientRetryPolicy =
    Schedule.recurWhile[ClientFailure](_.isTransient) &&
      Schedule.spaced(500.millis).jittered &&
      Schedule.recurs(2)

  val pollingSchedule =
    Schedule.recurUntil[RunStatus, Completed] { case c: Completed => c } &&
      Schedule.fixed(10.seconds).unit

  val pollingLogic =
    for {
      client <- ZIO.service[Client]
      run    <- client.submit().retry(transientRetryPolicy)
      result <- client.getStatus(run)
        .retry(transientRetryPolicy)
        .repeat(pollingSchedule)
        .timeoutFail(PollingTimeout)(1.minutes)
        .tapError(_ => client.cancel(run).retry(transientRetryPolicy))
    } yield result.get

  override def run = pollingLogic
    .flatMap(c => printLine(c.result))
    .provide(RestClient.live)
}
