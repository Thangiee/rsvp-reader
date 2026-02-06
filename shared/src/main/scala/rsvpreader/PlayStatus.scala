package rsvpreader

/** Current state of the RSVP playback engine. */
enum PlayStatus:
  case Playing
  case Paused
  case Finished
