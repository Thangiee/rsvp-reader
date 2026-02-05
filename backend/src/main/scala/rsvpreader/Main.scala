package rsvpreader

import kyo.*
import sttp.tapir.*
import sttp.tapir.server.netty.*

object Main extends KyoApp:

  val helloRoute: Unit < Routes = Routes.add(
    _.get.in("api" / "hello").out(stringBody)
  )(_ => SharedMessage.hello.message)

  val indexRoute: Unit < Routes = Routes.add(
    _.get.out(htmlBodyUtf8)
  )(_ => scala.io.Source.fromFile("frontend/index.html").mkString)

  val jsRoute: Unit < Routes = Routes.add(
    _.get
      .in("assets" / "main.js")
      .out(stringBodyUtf8AnyFormat(Codec.string.format(CodecFormat.TextJavascript())))
  )(_ => scala.io.Source.fromFile("frontend/target/scala-3.7.4/frontend-fastopt/main.js").mkString)

  run {
    for
      _     <- Routes.run(helloRoute.andThen(jsRoute).andThen(indexRoute))
      fiber <- Fiber.never
      _     <- Fiber.get(fiber)
    yield ()
  }
