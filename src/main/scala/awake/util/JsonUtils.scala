package awake.util

import io.circe.{ Decoder, Encoder, HCursor, Json }

import scala.concurrent.duration.FiniteDuration

object FiniteDurationJson {
  implicit val encoder: Encoder[FiniteDuration] = (a: FiniteDuration) => Json.obj(
    ("unit", Json.fromString(a.unit.toString.toLowerCase())),
    ("length", Json.fromLong(a.length)))

  implicit val decoder: Decoder[FiniteDuration] = (c: HCursor) => for {
    unit <- c.downField("unit").as[String]
    length <- c.downField("length").as[Long]
  } yield FiniteDuration(length, unit)
}