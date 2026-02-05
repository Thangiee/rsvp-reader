package rsvpreader

import kyo.*

case class RsvpConfig(
  baseWpm: Int = 300,
  startDelay: Duration = 500.millis,
  commaDelay: Duration = 150.millis,
  periodDelay: Duration = 300.millis,
  paragraphDelay: Duration = 500.millis,
  paragraphAutoPause: Boolean = false,
  wordLengthEnabled: Boolean = true,
  wordLengthBaseline: Int = 5,
  wordLengthFactor: Double = 0.1,
  orpEnabled: Boolean = true,
  trailWordCount: Int = 5
)
