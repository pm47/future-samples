import akka.actor.Status.Failure
import akka.actor.{Props, ActorSystem, Actor}
import akka.dispatch.{Future, Await}
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask


object SimpleActor extends App {

  case class StoreRequest(key: String, value: String)

  case class StoreOk(key: String)

  case class StoreFailure(reason: String)

  class StorageActor extends Actor {

    def receive = {
      case StoreRequest("", value) ⇒ sender ! StoreFailure("empty key")
      case StoreRequest(key, value) ⇒ {
        println("..........storing " + key)
        sender ! StoreOk(key)
      }
      case _ => sender ! Failure(new IllegalArgumentException)
    }

  }

  implicit val system = ActorSystem("MySystem")
  implicit val timeout = Timeout(60 seconds) // needed for `?` below

  val storActor = system.actorOf(Props[StorageActor])

  {
    println("> normal processing")
    val future = storActor ? StoreRequest("france", "paris")
    val res = Await.result(future, timeout.duration)
    println("   " + res)
  }

  {
    println("> exception thrown")
    try {
      val future = storActor ? "invalid-request"
      val res = Await.result(future, timeout.duration)
      println("   " + res)
    } catch {
      case e: Exception => println("   error : " + e.getClass)
    }
  }

  {
    println("> exception recovered")
    val future = (storActor ? "invalid-request") recover {
      case e: Exception => "error : " + e.getClass
    }
    val res = Await.result(future, timeout.duration)
    println("   " + res)
  }

  {
    println("> multiple actions")
    val map = Map("france" -> "paris", "germany" -> "berlin", "italy" -> "rome")
    val future = Future.traverse(map)(x => storActor ? StoreRequest(x._1, x._2))
    val res = Await.result(future, timeout.duration)
    res.foreach(x => println("   " + x))
  }

  {
    println("> multiple actions with one error properly handled")
    val map = Map("france" -> "paris", "germany" -> "berlin", "italy" -> "rome", "" -> "xxxx")
    val future = Future.traverse(map)(x => storActor ? StoreRequest(x._1, x._2))
    val res = Await.result(future, timeout.duration)
    res.foreach(x => println("   " + x))
  }

  {
    println("> multiple actions with one exception")
    try {
      val map = Map("france" -> "paris", "germany" -> "berlin", "italy" -> "rome", "" -> "xxxx")
      val future = Future.traverse(map)(x => if (x._1.isEmpty) {
        storActor ? "willfail"
      } else {
        storActor ? StoreRequest(x._1, x._2)
      })
      val res = Await.result(future, timeout.duration)
      res.foreach(x => println("   " + x))
    } catch {
      case e: Exception => println("   error : " + e.getClass)
    }
  }

  {
    println("> multiple actions with one exception recovered")
    val map = Map("france" -> "paris", "germany" -> "berlin", "italy" -> "rome", "" -> "xxxx")
    val future = Future.traverse(map)(x => (if (x._1.isEmpty) {
      storActor ? "willfail"
    } else {
      storActor ? StoreRequest(x._1, x._2)
    }) recover {
      case e: Exception => "error : " + e.getClass
    })
    val res = Await.result(future, timeout.duration)
    res.foreach(x => println("   " + x))
  }

  system.shutdown()
}



