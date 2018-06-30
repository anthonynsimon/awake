package awake.probe

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import awake.domain.Monitor
import awake.repo.InMemoryMonitorRepo

import scala.concurrent.{ExecutionContext, Future}

object ProbeServer {

  import akka.http.scaladsl.server.Directives._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.generic.auto._
  import awake.util.FiniteDurationJson._
  import io.circe.java8.time._

  def apply(probe: Probe)(implicit executionContext: ExecutionContext,
                          actorSystem: ActorSystem,
                          actorMaterializer: ActorMaterializer): Future[Http.ServerBinding] = {

    val routes = path("") {
      get {
        val future = InMemoryMonitorRepo.findPending(executionTime = ZonedDateTime.now())
        val transformed = future.map(_.take(10))
        complete(transformed)
      } ~ post {
        entity(as[Monitor]) { monitor =>
          val future = probe.poll(monitor)
          complete(future)
        }
      }
    }

    Http().bindAndHandle(routes, "localhost", 9090)
  }
}
