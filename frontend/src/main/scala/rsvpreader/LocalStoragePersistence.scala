package rsvpreader

import kyo.*
import org.scalajs.dom.window.localStorage
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.state.*

object LocalStoragePersistence extends Persistence:

  /** Synchronous load for bootstrap (before Kyo runtime is available). */
  def loadSync: DomainModel =
    val wpm = Maybe(localStorage.getItem("rsvp-wpm"))
      .flatMap(s => Maybe.fromOption(s.toIntOption)).filter(w => w >= 100 && w <= 1000).getOrElse(300)
    val centerMode = Maybe(localStorage.getItem("rsvp-centerMode"))
      .map(CenterMode.fromString).getOrElse(CenterMode.ORP)
    val contextSentences = Maybe(localStorage.getItem("rsvp-contextSentences"))
      .flatMap(s => Maybe.fromOption(s.toIntOption)).filter(n => n >= 1 && n <= 4).getOrElse(1)

    var bindings = KeyBindings.default
    KeyAction.values.foreach { action =>
      Maybe(localStorage.getItem(s"rsvp-key-${action.toString}")).foreach { key =>
        bindings = bindings.withBinding(action, key)
      }
    }

    DomainModel(
      viewState = ViewState(Span.empty, 0, PlayStatus.Paused, wpm),
      centerMode = centerMode,
      keyBindings = bindings,
      contextSentences = contextSentences
    )

  def load: DomainModel < Sync = Sync.defer(loadSync)

  def save(model: DomainModel): Unit < Sync = Sync.defer {
    localStorage.setItem("rsvp-wpm", model.viewState.wpm.toString)
    localStorage.setItem("rsvp-centerMode", model.centerMode.toString.toLowerCase)
    localStorage.setItem("rsvp-contextSentences", model.contextSentences.toString)
    KeyAction.values.foreach { action =>
      localStorage.setItem(s"rsvp-key-${action.toString}", model.keyBindings.keyFor(action))
    }
  }

  def savePosition(textHash: Int, index: Int): Unit < Sync = Sync.defer {
    localStorage.setItem("rsvp-position", s"$textHash:$index")
  }

  /** Synchronous load for bootstrap (before Kyo runtime is available). */
  def loadPositionSync: Maybe[(Int, Int)] =
    Maybe(localStorage.getItem("rsvp-position")).flatMap { raw =>
      val parts = raw.split(":")
      if parts.length == 2 then
        val result = for
          hash <- parts(0).toIntOption
          idx  <- parts(1).toIntOption
        yield (hash, idx)
        Maybe.fromOption(result)
      else Absent
    }

  def loadPosition: Maybe[(Int, Int)] < Sync = Sync.defer(loadPositionSync)
