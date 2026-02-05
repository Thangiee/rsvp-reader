package rsvpreader

/** Punctuation types that affect reading pace in RSVP display.
  * Each type maps to a configurable delay in RsvpConfig.
  */
enum Punctuation:
  case None
  case Comma      // includes ; :
  case Period     // includes ! ?
  case Paragraph  // end of paragraph
