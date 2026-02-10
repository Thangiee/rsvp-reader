package rsvpreader.state

import kyo.*
import munit.FunSuite
import rsvpreader.playback.*
import rsvpreader.config.*

class PersistenceSuite extends FunSuite:
  import AllowUnsafe.embrace.danger

  def runSync[A](effect: A < Sync): A =
    KyoApp.Unsafe.runAndBlock(5.seconds)(effect).getOrThrow

  val model = AppState(
    viewState = ViewState(Span.empty, 0, PlayStatus.Paused, 450),
    centerMode = CenterMode.First,
    keyBindings = KeyBindings.default.withBinding(KeyAction.PlayPause, "p"),
    contextSentences = 3
  )

  test("save then load round-trips AppState"):
    val store = scala.collection.mutable.Map.empty[String, String]
    val persistence = InMemoryPersistence(store)
    runSync {
      persistence.save(model).andThen(persistence.load)
    } match
      case loaded =>
        assertEquals(loaded.viewState.wpm, 450)
        assertEquals(loaded.centerMode, CenterMode.First)
        assertEquals(loaded.contextSentences, 3)
        assertEquals(loaded.keyBindings.keyFor(KeyAction.PlayPause), "p")

  test("load returns defaults when store is empty"):
    val store = scala.collection.mutable.Map.empty[String, String]
    val persistence = InMemoryPersistence(store)
    val loaded = runSync(persistence.load)
    assertEquals(loaded.centerMode, CenterMode.ORP)
    assertEquals(loaded.contextSentences, 1)

  test("savePosition and loadPosition round-trip"):
    val store = scala.collection.mutable.Map.empty[String, String]
    val persistence = InMemoryPersistence(store)
    val result = runSync {
      persistence.savePosition(12345, 42).andThen(persistence.loadPosition)
    }
    assertEquals(result, Present((12345, 42)))

  test("loadPosition returns Absent when not saved"):
    val store = scala.collection.mutable.Map.empty[String, String]
    val persistence = InMemoryPersistence(store)
    val result = runSync(persistence.loadPosition)
    assertEquals(result, Absent)
