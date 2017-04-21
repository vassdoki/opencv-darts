package experimental

import darts.{CaptureDevice, Config, DartFinder}
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_videoio.VideoCapture

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by vassdoki on 2017.04.21..
  */
object ParalelCamRead extends App{

  val capT1 = new CapTest(0)
  val capT2 = new CapTest(1)

  var m1 = new Mat
  var m2 = new Mat

  val cont = true
  while(cont) {
    println("------------------------sssss")
    val f1 = Future {
      println("f1 started")
      m1 = capT1.read(m1)
      println("f1 ended")
    }
    val f2 = Future {
      println("f2 started")
      m1 = capT2.read(m2)
      println("f2 ended")
    }
    println("before await")
    val r1 = Await.result(f1, 2 second)
    val r2 = Await.result(f2, 2 second)
    println("after await")
  }

}
