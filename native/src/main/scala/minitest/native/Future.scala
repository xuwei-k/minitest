package minitest

import scala.concurrent.duration.Duration
import scala.util._

class Future[+A] private[minitest] (val value: Try[A]) {

  //def map[B](f: A => B)(implicit executor: ExecutionContext): Future[B] =
  def map[B](f: A => B): Future[B] =
    new Future(value.map(f))

  def flatMap[B](f: A => Future[B])(implicit executor: ExecutionContext): Future[B] = {
    new Future(value.flatMap(f andThen(_.value)))
  }

  def onComplete[U](f: Try[A] => U)(implicit executor: ExecutionContext): Unit = {
    f(value)
  }

}

object Future {
  def apply[A](f: => A): Future[A] =
    new Future(Try(f))
  def successful[A](value: A): Future[A] =
    new Future(Success(value))
  def failed[A](e: Throwable): Future[A] =
    new Future(Failure(e))
}

trait ExecutionContext

object ExecutionContext {
  implicit val global: ExecutionContext = new ExecutionContext{}
}

object Promise {
  def apply[A](): Promise[A] = new Promise[A]()
}

class Promise[A] private (var value: Option[Try[A]] = None) {

  def success(value: A): this.type = {
    this.value = Some(Success(value))
    this
  }

  def future: Future[A] = {
    new Future(value.getOrElse(sys.error("not completed")))
  }

}

object Await {
  def result[A](future: Future[A], duration: Duration): A =
    future.value.get
}
