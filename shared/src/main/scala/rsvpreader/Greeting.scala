package rsvpreader

case class Greeting(message: String)

object SharedMessage:
  val hello: Greeting = Greeting("Hello, World!")
