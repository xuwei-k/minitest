package minitest

trait ExecutionContext

object ExecutionContext {
  val global: ExecutionContext = new ExecutionContext{}
}
