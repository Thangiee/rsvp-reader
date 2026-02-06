package rsvpreader

/** Punctuation types that affect reading pace in RSVP display.
  * Each type maps to a configurable delay in RsvpConfig.
  */
enum Punctuation:
  case None
  case Comma(text: String)      // includes ; :
  case Period(text: String)     // includes ! ?
  case Paragraph  // end of paragraph

object Punctuation:
  extension (p: Punctuation) def text: String = p match
    case None           => ""
    case Comma(t)       => t
    case Period(t)      => t
    case Paragraph      => ""
