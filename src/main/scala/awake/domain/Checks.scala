package awake.domain

import scala.concurrent.duration.FiniteDuration

sealed trait Check

final case class StatusCodeEquals(code: Int) extends Check

final case class ResponseTimeLessThan(duration: FiniteDuration) extends Check

final case class CheckTlsStillValid(numDays: Int) extends Check

final case class ProbeId(value: String) extends AnyVal
