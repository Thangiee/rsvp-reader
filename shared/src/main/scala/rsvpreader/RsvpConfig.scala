package rsvpreader

import kyo.*

/** Configuration for RSVP reading speed and timing behavior.
  *
  * @param baseWpm             Base words per minute (default 300)
  * @param startDelay          Delay before first word displays
  * @param commaDelay          Extra pause after commas, semicolons, colons
  * @param periodDelay         Extra pause after periods, exclamation, question marks
  * @param paragraphDelay      Additional pause at paragraph boundaries
  * @param paragraphAutoPause  If true, auto-pause at paragraph ends
  * @param wordLengthEnabled   If true, longer words display longer
  * @param wordLengthBaseline  Word length threshold for length bonus
  * @param wordLengthFactor    Multiplier for extra time per character over baseline
  * @param orpEnabled          If true, highlight ORP focus character
  * @param trailWordCount      Number of previous words to show in trail display
  */
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
  trailWordCount: Int = 5,
  centerMode: CenterMode = CenterMode.ORP,
  keyBindings: KeyBindings = KeyBindings.default,
  showSentenceContext: Boolean = true
)
