package polling

import zio.{IO, ULayer, ZLayer}

case class RestClient() extends Client {
  override def submit(): IO[ClientFailure, Run] = ???
  override def getStatus(run: Run): IO[ClientFailure, RunStatus] = ???
  override def cancel(run: Run): IO[ClientFailure, Unit] = ???
}

object RestClient {
  val live: ULayer[Client] = ZLayer.fromFunction(RestClient.apply _)
}