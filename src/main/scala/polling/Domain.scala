package polling

import zio.IO

trait Client {
  def submit(): IO[ClientFailure, Run]

  def getStatus(run: Run): IO[ClientFailure, RunStatus]

  def cancel(run: Run): IO[ClientFailure, Unit]
}

final case class ClientFailure(isTransient: Boolean, message: String) extends Exception(message)

final case class Run(id: String)

sealed trait RunStatus
object  RunStatus {
  final case class Completed(result: String) extends RunStatus
  case object Pending extends RunStatus
}