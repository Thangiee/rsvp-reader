package rsvpreader

import kyo.*
import org.scalajs.dom.window.localStorage
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.state.*
import rsvpreader.book.*

object LocalStoragePersistence extends Persistence:

  /** Synchronous load for bootstrap (before Kyo runtime is available). */
  def loadSync: AppState =
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

    AppState(
      viewState = ViewState(Span.empty, 0, PlayStatus.Paused, wpm),
      centerMode = centerMode,
      keyBindings = bindings,
      contextSentences = contextSentences,
      book = Book.fromPlainText(""),
      chapterIndex = 0
    )

  def load: AppState < Sync = Sync.defer(loadSync)

  def save(model: AppState): Unit < Sync = Sync.defer {
    localStorage.setItem("rsvp-wpm", model.viewState.wpm.toString)
    localStorage.setItem("rsvp-centerMode", model.centerMode.toString.toLowerCase)
    localStorage.setItem("rsvp-contextSentences", model.contextSentences.toString)
    KeyAction.values.foreach { action =>
      localStorage.setItem(s"rsvp-key-${action.toString}", model.keyBindings.keyFor(action))
    }
  }

  def savePosition(bookHash: Int, chapterIndex: Int, index: Int): Unit < Sync = Sync.defer {
    localStorage.setItem("rsvp-position", s"$bookHash:$chapterIndex:$index")
  }

  def savePositionSync(bookHash: Int, chapterIndex: Int, index: Int): Unit =
    localStorage.setItem("rsvp-position", s"$bookHash:$chapterIndex:$index")

  /** Synchronous load for bootstrap (before Kyo runtime is available). */
  def loadPositionSync: Maybe[(Int, Int, Int)] =
    Maybe(localStorage.getItem("rsvp-position")).flatMap { raw =>
      val parts = raw.split(":")
      if parts.length == 3 then
        val result = for
          hash       <- parts(0).toIntOption
          chapterIdx <- parts(1).toIntOption
          idx        <- parts(2).toIntOption
        yield (hash, chapterIdx, idx)
        Maybe.fromOption(result)
      else Absent
    }

  def loadPosition: Maybe[(Int, Int, Int)] < Sync = Sync.defer(loadPositionSync)

  def saveBook(book: Book): Unit < Sync = Sync.defer {
    saveBookSync(book)
  }

  def saveBookSync(book: Book): Unit =
    import scala.scalajs.js
    val chaptersJson = (0 until book.chapters.length).map { i =>
      val ch = book.chapters(i)
      js.JSON.stringify(js.Dynamic.literal(
        "title" -> ch.title,
        "text" -> ch.text
      ))
    }.mkString("[", ",", "]")
    val json = s"""{"title":${js.JSON.stringify(book.title)},"author":${js.JSON.stringify(book.author)},"chapters":$chaptersJson}"""
    localStorage.setItem("rsvp-book", json)

  def loadBook: Maybe[Book] < Sync = Sync.defer {
    loadBookSync
  }

  def loadBookSync: Maybe[Book] =
    import scala.scalajs.js
    Maybe(localStorage.getItem("rsvp-book")).flatMap { json =>
      try
        val parsed = js.JSON.parse(json)
        val title = parsed.selectDynamic("title").asInstanceOf[String]
        val author = parsed.selectDynamic("author").asInstanceOf[String]
        val chaptersArr = parsed.selectDynamic("chapters").asInstanceOf[js.Array[js.Dynamic]]
        val chapters = Span.from(chaptersArr.map { ch =>
          Chapter(
            ch.selectDynamic("title").asInstanceOf[String],
            ch.selectDynamic("text").asInstanceOf[String]
          )
        }.toArray)
        Maybe(Book(title, author, chapters))
      catch case _: Exception => Absent
    }
