package rsvpreader

/** Playback control commands sent to the PlaybackEngine via Channel. */
enum Command:
  case Pause
  case Resume
  case RestartSentence
  case RestartParagraph
  case SetSpeed(wpm: Int)
