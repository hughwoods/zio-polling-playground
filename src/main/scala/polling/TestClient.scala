package polling

import polling.RunStatus.{Completed, Pending}
import zio.{IO, Random, ZIO, ZLayer}

object TestClient extends Client {
  override def submit(): IO[ClientFailure, Run] = ZIO.succeed(Run("1234")).tap(_ => ZIO.log("queued"))

  private def logSucceed[A](message: String)(result: A) =
    ZIO.log("completed").flatMap(_ => ZIO.succeed(result))

  private def logFail[A](message: String)(error: A) =
    ZIO.log("completed").flatMap(_ => ZIO.fail(error))

  override def getStatus(run: Run): IO[ClientFailure, RunStatus] =
    for {
      int <- Random.nextIntBetween(0, 100)
      outcome <- int match {
        case x if x < 10 => logSucceed("completed")(Completed("congratulations"))
        case x if x < 20 => logFail("transient")(ClientFailure(true, ""))
        case x if x < 25 => logFail("fail")(ClientFailure(false, ""))
        case _ => logSucceed("pending")(Pending)
      }
    } yield outcome

  override def cancel(run: Run): IO[ClientFailure, Unit] = ZIO.log("cancelled")

  val layer: ZLayer[Any, Nothing, Client] = ZLayer.succeed(this)
}