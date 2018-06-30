package awake.probe

import java.time.ZonedDateTime

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.`Tls-Session-Info`
import awake.domain.{ Check, CheckTlsStillValid, ResponseTimeLessThan, StatusCodeEquals }

import scala.concurrent.duration._

trait Checker {
  type FailureMessage = String
  type Pass = Unit

  def check(res: HttpResponse, elapsed: FiniteDuration, checks: Seq[Check]): Either[FailureMessage, Pass] = {
    checks.foldLeft[Either[FailureMessage, Pass]](Right(Unit)) {
      case (acc, check) =>
        if (acc.isLeft) acc
        else {
          check match {
            case StatusCodeEquals(statusCode) => checkStatus(res)
            case ResponseTimeLessThan(duration) => checkResponseTime(duration, elapsed)
            case CheckTlsStillValid(numDays) => checkTlsCertificate(res, numDays)
          }
        }
    }
  }

  def checkResponseTime(expected: FiniteDuration, actual: FiniteDuration): Either[FailureMessage, Pass] = {
    if (actual.toMillis <= expected.toMillis) Right()
    else Left(s"Expected response time to be less than ${expected.toMillis} ms, but got: ${actual.toMillis}")
  }

  def checkStatus(res: HttpResponse): Either[FailureMessage, Pass] = {
    if (res.status.isSuccess()) Right()
    else Left(s"HTTP response status is not success: ${res.status.intValue()}")
  }

  def checkTlsCertificate(res: HttpResponse, stillValidForDays: Int): Either[FailureMessage, Pass] = {
    res
      .header[`Tls-Session-Info`]
      .flatMap(_.session.getPeerCertificateChain.headOption)
      .map(cert => {
        val now = ZonedDateTime.now().toInstant
        val expires = cert.getNotAfter.toInstant
        val offsetExpiration = expires.minusSeconds(stillValidForDays.days.toSeconds)

        if (expires.isBefore(now)) Left(s"TLS certificate expired on ${expires.toString}")
        else if (offsetExpiration.isBefore(now)) Left(s"TLS certificate will expire in less than $stillValidForDays days. Expiry date: $expires")
        else Right()
      })
      .getOrElse(Left("No TLS certificate in response"))
  }
}