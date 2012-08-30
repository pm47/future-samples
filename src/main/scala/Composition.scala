import akka.actor.Status.Failure
import akka.actor.{Props, ActorSystem, Actor}
import akka.dispatch.{Future, Await}
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask


object Composition extends App {

  case class GetFbDataRequest(id: Long, token: String)

  case class GetFbDataResponse(id: Long, data: String)

  class FaceBookActor extends Actor {

    def receive = {
      case GetFbDataRequest(-1, token) ⇒ throw new IllegalArgumentException("(fbactor) invalid id")
      case GetFbDataRequest(id, token) ⇒ {
        println("..........retrieving fb data for " + id)
        sender ! GetFbDataResponse(id, "some-data")
      }
      case _ => throw new IllegalArgumentException
    }

    override def preRestart(reason: Throwable, message: Option[Any]) {
      super.preRestart(reason, message)
      sender ! Failure(reason)
    }

  }

  case class StoreRequest(id: Long, data: String)

  case class StoreResponseOk(id: Long)

  case class StoreResponseFailure(reason: String)

  class StorageActor extends Actor {

    def receive = {
      case StoreRequest(-1, value) ⇒ throw new IllegalArgumentException("(storageactor) invalid id")
      case StoreRequest(key, value) ⇒ {
        println("..........storing " + key)
        sender ! StoreResponseOk(key)
      }
      case _ => throw new IllegalArgumentException
    }

    override def preRestart(reason: Throwable, message: Option[Any]) {
      super.preRestart(reason, message)
      sender ! Failure(reason)
    }

  }

  implicit val system = ActorSystem("MySystem")
  implicit val timeout = Timeout(60 seconds) // needed for `?` below

  val fbActor = system.actorOf(Props[FaceBookActor])
  val db1Actor = system.actorOf(Props[StorageActor])

  {
    println("> normal processing")
    val future = for {
      fbRes <- (fbActor ? GetFbDataRequest(1234, "my-token")).mapTo[GetFbDataResponse]
      db1res <- db1Actor ? StoreRequest(fbRes.id, fbRes.data)
    } yield db1res
    val res = Await.result(future, 60 second)
    println("   " + res)
  }

  {
    println("> multiple actions")
    val list = List((123, "token-123"), (456, "token-456"), (789, "token-789"))
    val future = Future.traverse(list)(x => for {
      fbRes <- (fbActor ? GetFbDataRequest(x._1, x._2)).mapTo[GetFbDataResponse]
      db1res <- db1Actor ? StoreRequest(fbRes.id, fbRes.data)
    } yield db1res)
    val res = Await.result(future, timeout.duration)
    res.foreach(x => println("   " + x))
  }

  /*{
    println("> multiple actions with one exception at step 1")
    try {
      val list = List((123, "token-123"), (-1, ""), (456, "token-456"), (789, "token-789"))
      val future = Future.traverse(list)(x => for {
        fbRes <- (fbActor ? GetFbDataRequest(x._1, x._2)).mapTo[GetFbDataResponse]
        db1res <- db1Actor ? StoreRequest(fbRes.id, fbRes.data)
      } yield db1res)
      val res = Await.result(future, timeout.duration)
      res.foreach(x => println("   " + x))
    } catch {
      case e: Exception => println("   error : " + e.getMessage)
    }
  }

  {
    println("> multiple actions with one exception at step 2")
    try {
      val list = List((123, "token-123"), (456, "token-456"), (789, "token-789"))
      val future = Future.traverse(list)(x => for {
        fbRes <- (fbActor ? GetFbDataRequest(x._1, x._2)).mapTo[GetFbDataResponse]
        db1res <- db1Actor ? StoreRequest(if (fbRes.id == 456) {
          -1
        } else (fbRes.id), fbRes.data)
      } yield db1res)
      val res = Await.result(future, timeout.duration)
      res.foreach(x => println("   " + x))
    } catch {
      case e: Exception => println("   error : " + e.getMessage)
    }
  }*/

  {
    println("> multiple actions with one exception at step 1 recovered")
    val list = List((123, "token-123"), (-1, ""), (456, "token-456"), (789, "token-789"))
    val future = Future.traverse(list)(x => (for {
      fbRes <- (fbActor ? GetFbDataRequest(x._1, x._2)).mapTo[GetFbDataResponse]
      db1res <- db1Actor ? StoreRequest(fbRes.id, fbRes.data)
    } yield db1res) recover {
      case e: Exception => "error : " + e.getMessage
    })
    val res = Await.result(future, timeout.duration)
    res.foreach(x => println("   " + x))
  }

  {
    println("> multiple actions with one exception at step 2 recovered")
    val list = List((123, "token-123"), (456, "token-456"), (789, "token-789"))
    val future = Future.traverse(list)(x => (for {
      fbRes <- (fbActor ? GetFbDataRequest(x._1, x._2)).mapTo[GetFbDataResponse]
      db1res <- db1Actor ? StoreRequest((if (fbRes.id == 456) {
        -1
      } else (fbRes.id)), fbRes.data)
    } yield db1res) recover {
      case e: Exception => "error : " + e.getMessage
    })
    val res = Await.result(future, timeout.duration)
    res.foreach(x => println("   " + x))
  }

  system.shutdown()

}


