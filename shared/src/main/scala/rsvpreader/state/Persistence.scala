package rsvpreader.state

import kyo.*
import rsvpreader.playback.*
import rsvpreader.config.*

/** Abstraction for persisting AppState settings and playback position.
  *
  * Implemented by LocalStoragePersistence (browser) and InMemoryPersistence (tests).
  */
trait Persistence:
  def load: AppState < Sync
  def save(model: AppState): Unit < Sync
  def savePosition(textHash: Int, index: Int): Unit < Sync
  def loadPosition: Maybe[(Int, Int)] < Sync

/** In-memory Persistence backed by a mutable Map, used for testing. */
class InMemoryPersistence(store: scala.collection.mutable.Map[String, String]) extends Persistence:

  def load: AppState < Sync = Sync.defer {
    val wpm = store.get("rsvp-wpm").flatMap(_.toIntOption).getOrElse(300)
    val centerMode = store.get("rsvp-centerMode").map(CenterMode.fromString).getOrElse(CenterMode.ORP)
    val contextSentences = store.get("rsvp-contextSentences").flatMap(_.toIntOption).filter(n => n >= 1 && n <= 4).getOrElse(1)

    var bindings = KeyBindings.default
    KeyAction.values.foreach { action =>
      store.get(s"rsvp-key-${action.toString}").foreach { key =>
        bindings = bindings.withBinding(action, key)
      }
    }

    AppState(
      viewState = ViewState(Span.empty, 0, PlayStatus.Paused, wpm),
      centerMode = centerMode,
      keyBindings = bindings,
      contextSentences = contextSentences
    )
  }

  def save(model: AppState): Unit < Sync = Sync.defer {
    store("rsvp-wpm") = model.viewState.wpm.toString
    store("rsvp-centerMode") = model.centerMode.toString.toLowerCase
    store("rsvp-contextSentences") = model.contextSentences.toString
    KeyAction.values.foreach { action =>
      store(s"rsvp-key-${action.toString}") = model.keyBindings.keyFor(action)
    }
  }

  def savePosition(textHash: Int, index: Int): Unit < Sync = Sync.defer {
    store("rsvp-position") = s"$textHash:$index"
  }

  def loadPosition: Maybe[(Int, Int)] < Sync = Sync.defer {
    Maybe.fromOption {
      store.get("rsvp-position").flatMap { raw =>
        val parts = raw.split(":")
        if parts.length == 2 then
          for
            hash <- parts(0).toIntOption
            idx  <- parts(1).toIntOption
          yield (hash, idx)
        else None
      }
    }
  }
