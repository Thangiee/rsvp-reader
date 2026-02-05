package rsvpreader

import munit.FunSuite

class CommandSuite extends FunSuite:

  test("Command.Back stores word count"):
    val cmd: Command.Back = Command.Back(10)
    assertEquals(cmd.words, 10)

  test("Command.SetSpeed stores wpm"):
    val cmd: Command.SetSpeed = Command.SetSpeed(500)
    assertEquals(cmd.wpm, 500)

  test("PlayStatus has all expected cases"):
    val cases = PlayStatus.values.toSet
    assertEquals(cases.size, 3)
    assert(cases.contains(PlayStatus.Playing), "should contain Playing")
    assert(cases.contains(PlayStatus.Paused), "should contain Paused")
    assert(cases.contains(PlayStatus.Stopped), "should contain Stopped")
