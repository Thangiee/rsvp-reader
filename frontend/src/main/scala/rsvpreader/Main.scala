package rsvpreader

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

object Main:
  def main(args: Array[String]): Unit =
    val messageVar = Var("Loading...")

    for
      response <- dom.fetch("/api/hello").toFuture
      text     <- response.text().toFuture
    do
      messageVar.set(text)

    val app = div(
      h1("RSVP Reader"),
      p(child.text <-- messageVar.signal)
    )

    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
