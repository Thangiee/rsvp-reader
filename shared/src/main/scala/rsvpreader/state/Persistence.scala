package rsvpreader.state

import kyo.*
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.book.*

/** Abstraction for persisting AppState settings and playback position.
  *
  * Implemented by LocalStoragePersistence (browser) and InMemoryPersistence (tests).
  */
trait Persistence:
  def load: AppState < Sync
  def save(model: AppState): Unit < Sync
  def savePosition(bookHash: Int, chapterIndex: Int, index: Int): Unit < Sync
  def loadPosition: Maybe[(Int, Int, Int)] < Sync
  def saveBook(book: Book): Unit < Sync
  def loadBook: Maybe[Book] < Sync

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
      contextSentences = contextSentences,
      book = Book.fromPlainText(""),
      chapterIndex = 0
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

  def savePosition(bookHash: Int, chapterIndex: Int, index: Int): Unit < Sync = Sync.defer {
    store("rsvp-position") = s"$bookHash:$chapterIndex:$index"
  }

  def loadPosition: Maybe[(Int, Int, Int)] < Sync = Sync.defer {
    Maybe.fromOption {
      store.get("rsvp-position").flatMap { raw =>
        val parts = raw.split(":")
        if parts.length == 3 then
          for
            hash       <- parts(0).toIntOption
            chapterIdx <- parts(1).toIntOption
            idx        <- parts(2).toIntOption
          yield (hash, chapterIdx, idx)
        else None
      }
    }
  }

  def saveBook(book: Book): Unit < Sync = Sync.defer {
    store("rsvp-book") = s"${book.title}|${book.author}|${book.chapters.length}"
  }

  def loadBook: Maybe[Book] < Sync = Sync.defer {
    Absent
  }
