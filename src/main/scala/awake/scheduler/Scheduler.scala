package awake.scheduler

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import awake.repo.{MonitorRepo, ProbeRepo}
import awake.scheduler.Counter.ShowCount
import awake.domain.{Monitor, ProbeResult}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait Cancellable {
  def apply(): Future[Boolean]
}

class StreamScheduler(monitorRepo: MonitorRepo, probeRepo: ProbeRepo)(implicit executionContext: ExecutionContext,
                                                                      actorSystem: ActorSystem,
                                                                      actorMaterializer: ActorMaterializer) {

  def start(interval: FiniteDuration = 1.minute, concurrentRequests: Int = 50): Cancellable = {
    val counter = actorSystem.actorOf(Counter.props())
    val scheduledCount = actorSystem.scheduler.schedule(0.seconds, 1.seconds,
      () => counter ! ShowCount(cnt => s"Handled $cnt polls per second"))

    val cancellable = Source
      .tick(0.seconds, interval, Unit)
      .mapAsync(1)(_ => monitorRepo.findPending(ZonedDateTime.now(), touch = true))
      .mapConcat[Monitor](_.toList)
      .mapAsyncUnordered(concurrentRequests) { monitor =>
        val probes = monitor.probes.flatMap(probeRepo.lookup)
        val futures = probes.map(probe => probe.poll(monitor))
        Future.sequence(futures).map(results => (monitor, results))
      }
      .log("error polling probe")
      .map(res => Counter.MonitorResult(res._1, res._2))
      .to(Sink.actorRef(counter, PoisonPill))
      .run()

    () =>
      Future.successful({
        val a = cancellable.cancel()
        val b = scheduledCount.cancel()
        a && b
      })
  }
}

class Counter extends Actor {

  import Counter._

  private var counter = 0

  override def receive: Receive = {
    case MonitorResult(_, _) => counter += 1
    case ShowCount => println(counter)
  }
}

object Counter {

  case class ShowCount(messageFunc: Int => String)

  case class MonitorResult(monitor: Monitor, results: Seq[ProbeResult])

  def props(): Props = Props[Counter]
}
