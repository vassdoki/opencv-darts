package experimental

import darts.{Config, Util}
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgcodecs.imwrite
import org.bytedeco.javacpp.opencv_videoio.{CAP_PROP_FOURCC, CAP_PROP_FPS, CAP_PROP_FRAME_HEIGHT, CAP_PROP_FRAME_WIDTH, CV_CAP_PROP_FOURCC, CV_FOURCC, VideoCapture}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by vassdoki on 2017.08.07..
  */
object FpsTest extends App{
  parTest

  def parTest = {
    val width = 640
    val height = 480

//    val camIds = (0 to 4)
    val camIds = (0 to 0)
    val vcs = camIds.map(i => new VideoCapture(i))
    for(vc <- vcs) {
      vc.set(CAP_PROP_FRAME_WIDTH, width) // 640 // 1280
      vc.set(CAP_PROP_FRAME_HEIGHT, height) // 480 // 720
    }
    val imgs = camIds.map(i => new Mat())

    val start = System.currentTimeMillis()
    var count = 0

    while (true) {
      val futs = camIds.map(i => Future{
        vcs(i).read(imgs(i))
        //        imwrite(s"test$i-$count.png", imgs(i))
      })
      val res = for(f <- futs) {
        Await.result(f, 2 second)
      }
      count += 1
      println(f"===== ${(count.toFloat / (System.currentTimeMillis() - start)) * 1000}%.1f fps")
    }
  }
  def openAndCloseTest = {
    println("opening video 1")
    val vc1 = new VideoCapture(1)
    vc1.set(CAP_PROP_FRAME_WIDTH, 1920) // 640 // 1280
    vc1.set(CAP_PROP_FRAME_HEIGHT, 1080) // 480 // 720
    println("opening video 1 done")
    vc1.release()
    //  println("opening video 2")
    //  val vc2 = new VideoCapture(2)
    //  vc2.set(CAP_PROP_FRAME_WIDTH, 1920) // 640 // 1280
    //  vc2.set(CAP_PROP_FRAME_HEIGHT, 1080) // 480 // 720
    //  println("opening video 2 done")

    //  val vc2 = new VideoCapture(0)
    //  vc2.set(CAP_PROP_FRAME_WIDTH, 1920) // 640 // 1280
    //  vc2.set(CAP_PROP_FRAME_HEIGHT, 1080) // 480 // 720

    println(s"CV_CAP: ${vc1.get(CV_CAP_PROP_FOURCC)}")
    println(s"CAP: ${vc1.get(CAP_PROP_FOURCC)}")

    val f1 = new Mat
    val f2 = new Mat
    val start = System.currentTimeMillis()
    var count = 0

    while (true) {
      if (count % 2 == 0) {
        println("open cam 1")
        vc1.open(1)
        println("read cam 1")
        vc1.read(f1)
        println("read cam 1 done, waiting")
        vc1.release()
        println("close cam 1")
      } else {
        println("open cam 2")
        vc1.open(2)
        println("read cam 2")
        vc1.read(f1)
        println("read cam 2 done, waiting")
        vc1.release()
        println("close cam 2")
      }
      //    Util.show(f1, "captured 1")
      //    vc2.read(f2)
      //    Util.show(f2, "captured 2")
      count += 1
      println(f"===== ${(count.toFloat / (System.currentTimeMillis() - start)) * 1000}%.1f fps")

    }
  }


}
