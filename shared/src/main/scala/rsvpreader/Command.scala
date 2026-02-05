package rsvpreader

enum Command:
  case Pause
  case Resume
  case Back(words: Int)
  case RestartSentence
  case SetSpeed(wpm: Int)
  case Stop
