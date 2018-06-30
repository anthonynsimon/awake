package awake.domain

import java.time.ZonedDateTime

import scala.concurrent.duration.FiniteDuration

case class Monitor(id: String,
                   request: RequestConf,
                   interval: FiniteDuration,
                   lastRun: ZonedDateTime,
                   checks: Seq[Check],
                   probes: Seq[ProbeId])

case class RequestConf(url: String, headers: Map[String, String])
