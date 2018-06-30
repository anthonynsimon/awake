package awake

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import awake.domain.ProbeId
import awake.probe._
import awake.repo.{InMemoryMonitorRepo, MonitorRepo, ProbeRepo, StaticProbeRepo}
import awake.scheduler.StreamScheduler

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Try

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("awake")
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    Try(args(0)).toOption match {
      case Some(cmd) =>
        cmd match {
          case "scheduler" => {
            val monitorRepo: MonitorRepo = InMemoryMonitorRepo
            val probeRepo: ProbeRepo = StaticProbeRepo(Map(ProbeId("local-1") -> new RemoteProbe("http://localhost:9090")))
            val scheduler = new StreamScheduler(monitorRepo, probeRepo)
            val cancel = scheduler.start(5.seconds)
            Await.ready(system.whenTerminated, Duration.Inf)
          }
          case "probe" => {
            val binding = ProbeServer(new StreamingProbe)
            Await.ready(system.whenTerminated, Duration.Inf)
          }
          case _ => println("unknown command")
        }
      case None => println("no command")
    }
  }
}

case class SchedulerConf(pollInterval: FiniteDuration, maxConcurrentRequests: Int, probes: RemoteProbesConf)

case class RemoteProbeConf(id: String, host: String)
case class RemoteProbesConf(probes: Seq[RemoteProbeConf])
