package rsvpreader.playback

/** Playback control commands sent to the PlaybackEngine via Channel. */
enum Command:
  case Pause
  case Resume
  case RestartSentence
  case RestartParagraph
  case SetSpeed(wpm: Int)
  case JumpToIndex(index: Int)
  /** Signals the engine to exit its playback loop so the engine loop
    * can take new tokens from the tokens channel. Sent by onTextLoaded
    * before offering the new tokenized text.
    */
  case LoadText
