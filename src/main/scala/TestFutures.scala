import akka.dispatch.{Await, Future}
import akka.util.duration._
import akka.actor.ActorSystem
import compat.Platform

/**
 * Created with IntelliJ IDEA.
 * User: pm
 * Date: 30/08/12
 * Time: 12:29
 * To change this template use File | Settings | File Templates.
 */
object TestFutures extends App {

  def getFuture(x: String): Future[String] = {
    Future {
      Thread.sleep(5000)
      " "
    }
  }

  implicit val system = ActorSystem("MySystem")

  val start = Platform.currentTime

  val f1: Future[String] = {
    Future {
      Thread.sleep(5000)
      "Hello"
    }
  }

  val f2: Future[String] = {
    Future {
      Thread.sleep(5000)
      " "
    }
  }

  val f3: Future[String] = {
    Future {
      Thread.sleep(5000)
      "World"
    }
  }

  val f = for {
    a <- f1
    b <- getFuture(a)
    c <- f3
  } yield a + b + c

  val result = Await.result(f, 60 second)

  val end = Platform.currentTime

  println(result)
  println(end - start)

}
