package rsvpreader

import kyo.*

def calculateDelay(token: Token, config: RsvpConfig): Duration =
  val base = (60000.0 / config.baseWpm).toLong.millis

  val lengthBonus =
    if config.wordLengthEnabled && token.text.length > config.wordLengthBaseline then
      val extraChars = token.text.length - config.wordLengthBaseline
      Duration.fromNanos((base.toNanos * extraChars * config.wordLengthFactor).toLong)
    else Duration.Zero

  val punctPause = token.punctuation match
    case Punctuation.Period    => config.periodDelay
    case Punctuation.Comma     => config.commaDelay
    case Punctuation.Paragraph => config.periodDelay + config.paragraphDelay
    case Punctuation.None      => Duration.Zero

  base + lengthBonus + punctPause
