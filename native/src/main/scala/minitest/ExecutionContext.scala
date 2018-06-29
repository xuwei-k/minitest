package minitest

trait ExecutionContext

object ExecutionContext {
  implicit val global: ExecutionContext = new ExecutionContext{}
}
