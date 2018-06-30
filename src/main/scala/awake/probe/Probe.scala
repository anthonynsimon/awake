package awake.probe

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy, QueueOfferResult }
import akka.util.ByteString
import awake.domain._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

trait Probe {
  def poll(monitor: Monitor): Future[ProbeResult]
}

class StreamingProbe(implicit
  executionContext: ExecutionContext,
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer)
  extends Probe
  with Checker {

  private val client = Http(actorSystem)

  private val streamProcess = Source
    .queue[(Monitor, HttpRequest, Promise[ProbeResult])](1000, OverflowStrategy.backpressure)
    .mapAsyncUnordered(10) {
      case (monitor, request, promise) =>
        val start = System.nanoTime()
        client.singleRequest(request).map(response => (monitor, response, promise, start))
    }
    .mapAsyncUnordered(10) {
      case (monitor, response, promise, start) =>
        val duration = (System.nanoTime() - start).nanos
        handleResponse(response, duration, monitor.checks).map(res => (res, promise))
    }
    .to(Sink.foreach {
      case (result, promise) => promise.complete(Try(result))
    })
    .run()

  override def poll(monitor: Monitor): Future[ProbeResult] = {
    val request = buildRequest(monitor.request)
    val promise = Promise[ProbeResult]()
    streamProcess.offer(monitor, request, promise).flatMap {
      case QueueOfferResult.Enqueued => promise.future
      case _ => Future.failed(new RuntimeException("Could not enqueue monitor for processing"))
    }
  }

  private def handleResponse(res: HttpResponse, duration: FiniteDuration, checks: Seq[Check]): Future[ProbeResult] = {
    res.entity.discardBytes().future().map { _ =>
      val result = check(res, duration, checks)
      result match {
        case Left(errMsg) => Failed(duration.toMillis.millis, errMsg)
        case Right(_) => Passed(duration.toMillis.millis)
      }
    }
  }

  private def buildRequest(request: RequestConf): HttpRequest = {
    val headers = buildHeaders(request.headers).getOrElse(Seq.empty)
    HttpRequest(uri = Uri(request.url), headers = headers.toList)
  }

  private def buildHeaders(headers: Map[String, String]): Try[Seq[HttpHeader]] = Try {
    headers.toSeq
      .map(x => HttpHeader.parse(x._1, x._2))
      .map {
        case ParsingResult.Ok(header, _) => header
        case ParsingResult.Error(error) => throw new Exception(error.summary)
      }
  }
}

class RemoteProbe(val address: String)(implicit
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer,
  executionContext: ExecutionContext)
  extends Probe {

  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._
  import awake.util.FiniteDurationJson._
  import io.circe.java8.time._

  private val client = Http()

  override def poll(monitor: Monitor): Future[ProbeResult] = {
    client.singleRequest(buildRequest(monitor))
      .flatMap {
        case HttpResponse(status, _, entity, _) if status.isSuccess() => {
          Unmarshal(entity).to[String]
            .map(str => decode[ProbeResult](str))
            .map {
              case Right(result) => result
              case Left(err) => throw err
            }
        }
        case res => throw new Exception(s"Bad response from probe: ${res.status}")
      }
  }

  private def buildRequest(monitor: Monitor): HttpRequest =
    HttpRequest(
      method = HttpMethods.POST,
      uri = address,
      entity = HttpEntity(
        contentType = ContentTypes.`application/json`,
        data = ByteString(monitor.asJson.noSpaces)))
}
