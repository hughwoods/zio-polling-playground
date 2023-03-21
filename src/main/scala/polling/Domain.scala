package polling

import zio.IO

trait Client {
  def submit(): IO[ClientFailure, Run]

  def getStatus(run: Run): IO[ClientFailure, RunStatus]

  def cancel(run: Run): IO[ClientFailure, Unit]
}

sealed abstract class PollingFailure(message: String)
final case class ClientFailure(isTransient: Boolean, message: String) extends PollingFailure(message)
case object PollingTimeout extends PollingFailure(s"The polling operation timed out")

final case class Run(id: String)

sealed trait RunStatus
object  RunStatus {
  final case class Completed(result: String) extends RunStatus
  case object Pending extends RunStatus
}