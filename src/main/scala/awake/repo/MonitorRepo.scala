package awake.repo

import java.time.ZonedDateTime

import awake._
import awake.domain._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

trait MonitorRepo {
  def findPending(executionTime: ZonedDateTime, touch: Boolean): Future[Seq[Monitor]]
}

object InMemoryMonitorRepo extends MonitorRepo {
  private val monitors = (0 to 1000).map(
    i =>
      s"my-uuid-$i" -> Monitor(
        s"my-uuid-$i",
        RequestConf("https://jsonplaceholder.typicode.com/todos", Map.empty),
        5.seconds,
        ZonedDateTime.now().minusMinutes(1.minute.toMinutes),
        Seq(ResponseTimeLessThan(500.millis), StatusCodeEquals(200)),
        Seq(ProbeId("local-1"))))
  private val data = mutable.Map[String, Monitor](monitors: _*)

  override def findPending(executionTime: ZonedDateTime, touch: Boolean = false): Future[Seq[Monitor]] =
    Future.successful {
      val filtered = data.values
        .filter(m => m.lastRun.plusSeconds(m.interval.toSeconds).isBefore(executionTime))
        .toSeq
      if (touch) filtered.foreach(monitor => data.update(monitor.id, monitor.copy(lastRun = executionTime)))
      filtered
    }
}
