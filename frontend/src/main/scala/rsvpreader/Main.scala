package rsvpreader

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import kyo.{Var, Var as KyoVar, *}

import scala.concurrent.Future

case class RsvpState(words: kyo.Span[String], idx: Int) {
  def current: Maybe[String] =
    if idx < words.length then Maybe(words(idx)) else Maybe.empty

  def advance: RsvpState = this.copy(idx = idx + 1)

  def isDone: Boolean = idx >= words.length
}

def rsvp(text: String, wpm: Int)(render: String => Unit) =
  val words = Span.from(text.split("\\s+"))
  val delay = (60000.0 / wpm).toLong.millis

  Loop(RsvpState(words, 0)): s =>
    s.current match
      case Absent => Loop.done(())
      case Present(word) =>
        direct {
          render(word)
          Async.sleep(delay).now
          Loop.continue(s.advance)
        }

object Main extends KyoApp:
  val messageVar = LaminarVar("Loading...")

  val app = div(
    h1("RSVP Reader 123!!"),
    p(child.text <-- messageVar.signal)
  )
  renderOnDomContentLoaded(dom.document.getElementById("app"), app)

  val text = "The map method automatically updates the set of pending effects. When you apply map to computations that have different pending effects, Kyo reconciles these into a new computation type that combines all the unique pending effects from both operands."

  run {
    direct {
      rsvp(text, 500)(messageVar.set).now
    }
  }

