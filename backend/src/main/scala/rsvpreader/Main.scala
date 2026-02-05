package rsvpreader

import kyo.*
import sttp.tapir.*
import sttp.tapir.server.netty.*

import java.nio.file.Paths

object Main extends KyoApp:

  val jsRoute: Unit < Routes = Routes.add(
    _.get
      .in("assets" / "main.js")
      .out(stringBodyUtf8AnyFormat(Codec.string.format(CodecFormat.TextJavascript())))
  )(_ => scala.io.Source.fromFile("frontend/target/scala-3.7.4/frontend-fastopt/main.js").mkString)

  val indexRoute: Unit < Routes = Routes.add(
    _.get.out(htmlBodyUtf8)
  )(_ => scala.io.Source.fromFile("frontend/index.html").mkString)

  run {
    for
      _     <- Routes.run(jsRoute.andThen(indexRoute))
      fiber <- Fiber.never
      _     <- Fiber.get(fiber)
    yield ()
  }
