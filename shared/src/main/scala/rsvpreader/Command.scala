package rsvpreader

/** Playback control commands sent to the PlaybackEngine via Channel. */
enum Command:
  case Pause
  case Resume
  case Back(words: Int)
  case RestartSentence
  case SetSpeed(wpm: Int)
  case Stop
