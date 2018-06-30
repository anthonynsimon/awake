package awake.domain

import scala.concurrent.duration.FiniteDuration

sealed trait ProbeResult

final case class Passed(responseTime: FiniteDuration) extends ProbeResult

final case class Failed(responseTime: FiniteDuration, message: String) extends ProbeResult

case class Timeout(duration: FiniteDuration) extends ProbeResult