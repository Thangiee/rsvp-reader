package rsvpreader.config

/** ORP letter centering mode for focus word display.
  * - ORP: Center on the optimal recognition point (default)
  * - First: Center on the first letter
  * - None: No centering (left-aligned)
  */
enum CenterMode:
  case ORP, First, None

/** Parses CenterMode from a persisted string, defaulting to ORP. */
object CenterMode:
  def fromString(s: String): CenterMode = s.toLowerCase match
    case "orp"   => ORP
    case "first" => First
    case "none"  => None
    case _       => ORP // default
