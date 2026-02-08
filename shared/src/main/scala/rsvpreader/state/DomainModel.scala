package rsvpreader.state

import kyo.*
import rsvpreader.playback.*
import rsvpreader.config.*

case class DomainModel(
  viewState: ViewState,
  centerMode: CenterMode,
  keyBindings: KeyBindings,
  contextSentences: Int
)

object DomainModel:
  def initial: DomainModel = DomainModel(
    viewState = ViewState(Span.empty, 0, PlayStatus.Paused, 300),
    centerMode = CenterMode.ORP,
    keyBindings = KeyBindings.default,
    contextSentences = 1
  )

  def progressPercent(m: DomainModel): Double =
    val s = m.viewState
    if s.tokens.length == 0 then 0.0
    else if s.tokens.length <= 1 then 100.0
    else (s.index.toDouble / (s.tokens.length - 1)) * 100.0

  def timeRemaining(m: DomainModel): String =
    val s = m.viewState
    val remaining = s.tokens.length - s.index
    val minutes = remaining.toDouble / s.wpm
    if minutes < 1 then "< 1 min" else s"~${minutes.toInt} min"

  def wordProgress(m: DomainModel): String =
    val s = m.viewState
    val display = Math.min(s.index + 1, s.tokens.length)
    s"$display / ${s.tokens.length}"

  def statusDotCls(m: DomainModel): String =
    m.viewState.status match
      case PlayStatus.Playing  => "status-dot playing"
      case PlayStatus.Paused   => "status-dot paused"
      case PlayStatus.Finished => "status-dot paused"

  def focusContainerCls(m: DomainModel): String =
    val s = m.viewState
    val base = "focus-container"
    val playing = if s.status == PlayStatus.Playing then " playing" else ""
    val expanded = if (s.status == PlayStatus.Paused || s.status == PlayStatus.Finished) && s.tokens.length > 0 then " expanded" else ""
    base + playing + expanded

  def statusText(m: DomainModel): String =
    m.viewState.status.toString
