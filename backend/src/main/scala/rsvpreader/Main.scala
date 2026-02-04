package rsvpreader

import kyo.*
import sttp.tapir.*
import sttp.tapir.server.netty.*

object Main extends KyoApp:

  val helloRoute: Unit < Routes = Routes.add(
    _.get.in("api" / "hello").out(stringBody)
  )(_ => SharedMessage.hello.message)

  run {
    Routes.run(helloRoute)
  }
