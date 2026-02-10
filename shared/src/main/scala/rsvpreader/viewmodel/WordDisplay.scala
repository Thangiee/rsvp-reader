package rsvpreader.viewmodel

/** A word prepared for rendering in the sentence context view.
  *
  * @param text       Word text including trailing punctuation
  * @param cssClass   CSS class for styling (e.g. "sentence-word current", "sentence-word dim")
  * @param isCurrent  Whether this is the word currently being displayed in the RSVP focus
  */
case class WordDisplay(text: String, cssClass: String, isCurrent: Boolean)
