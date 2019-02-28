package experimental

import darts.{Config, Util}
import experimental.FpsTest.OptionMap
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgcodecs.imwrite
import org.bytedeco.javacpp.opencv_videoio.{CAP_PROP_FOURCC, CAP_PROP_FPS, CAP_PROP_FRAME_HEIGHT, CAP_PROP_FRAME_WIDTH, CV_CAP_PROP_FOURCC, CV_FOURCC, VideoCapture}
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by vassdoki on 2017.08.07..
  */
object FpsTest extends App{

  val arglist = args.toList
  type OptionMap = Map[Symbol, Any]

  def nextOption(map : OptionMap, list: List[String]) : OptionMap = {
    def isSwitch(s : String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--width" :: value :: tail => nextOption(map ++ Map('width -> value.toInt), tail)
      case "--height" :: value :: tail => nextOption(map ++ Map('height -> value.toInt), tail)
      case "--cam" :: value :: tail => nextOption(map ++ Map('camFirst -> value.toInt) ++ Map('camLast -> value.toInt), tail)
      case "--cam-range" :: value1 :: value2 :: tail => nextOption(map ++ Map('camFirst -> value1.toInt) ++ Map('camLast -> value2.toInt), tail)
      case "--show-window" :: tail => nextOption(map ++ Map('showWindow -> true), tail)
      case "--st" :: tail => nextOption(map ++ Map('singleThread -> true), tail)
      case "--save" :: value :: tail => nextOption(map ++ Map('save -> value.toString), tail)
      case "--help" :: tail => nextOption(map ++ Map('help -> true), tail)
      case option :: tail => println("Unknown option "+option)
        nextOption(map ++ Map('help -> true), tail)
    }
  }

  var defaults: OptionMap = Map('width -> 1920, 'height -> 1080, 'camFirst -> 0, 'camLast -> 0, 'showWindow -> false, 'singleThread -> false, 'help -> false, 'save -> "")
  //  var defaults: OptionMap = Map('width -> 1024, 'height -> 576, 'camFirst -> 0, 'camLast -> 0, 'showWindow -> false, 'help -> false)
  val options = nextOption(Map(),arglist)
  for((k, v: Any) <- options) {
    defaults = defaults ++ Map(k -> v)
  }
  println(defaults)
  def printHelp = {
    println(
      """
        |      --width  <Number>             Capture width
        |      --height <Number>             Capture height
        |      --cam    <Number>             Only one camera, /dev/videoX
        |      --cam-range <Number> <Number> Read cameras from X to Y
        |      --show-window                 Show the read images
        |      --st                          Single Thread
        |      --save <dir>                  Save the read images to dir
        |      --help                        This help
        |
      """.stripMargin)
  }


  if (defaults('help).asInstanceOf[Boolean]) {
    printHelp
  } else {
    parTest
  }



  def parTest = {

    //    val camIds = (0 to 4)
    val camIds = (defaults('camFirst).asInstanceOf[Int] to defaults('camLast).asInstanceOf[Int])
    val vcs = camIds.map(i => {
      println(s"Initialize camera $i")
      val res = new VideoCapture(i)
      if (!res.isOpened) {
        println(s"*****************************************************")
        println(s"ERROR Camera ${i} is opened: ${res.isOpened}")
        println(s"Do the current user have right to read /dev/video${i} ?")
        println("Maybe put it into video group?")
        println(s"*****************************************************")

      }
      res
    })
    for(vc <- vcs) {
      val width = defaults('width).asInstanceOf[Int]
      val height = defaults('height).asInstanceOf[Int]
      println(s"set resolution for camera $width x $height")
      vc.set(CV_CAP_PROP_FOURCC,CV_FOURCC('M','J','P','G'))
      vc.set(CAP_PROP_FRAME_WIDTH, width) // 640 // 1280
      vc.set(CAP_PROP_FRAME_HEIGHT, height) // 480 // 720
    }
    val imgs = camIds.map(i => new Mat())
    val imgs2 = camIds.map(i => new Mat())

    var start = System.currentTimeMillis()
    var count = -5

    val saveDir = defaults('save).asInstanceOf[String]

    if (defaults('singleThread).asInstanceOf[Boolean]) {
      while (true) {
        camIds.map(i => {
          var c = i - defaults('camFirst).asInstanceOf[Int]
          vcs(c).read(imgs(c))
          if (imgs(c) == null) {
            println(s"Read image is null")
          }
          if (imgs(c) != null && (imgs(c).rows == 0 || imgs(c).cols == 0)) {
            println(s"Image read from camera(i) has size == 0")
          }
          if (defaults('showWindow).asInstanceOf[Boolean]) {
            Util.show(imgs(c), s"cam: $i")
            if (saveDir != "") {
              val lastFilename = s"${Config.timeFormatter.print(DateTime.now)}"
              imwrite(s"$saveDir/d$i-$lastFilename.png", imgs(c))
              Thread.sleep(500)
            }
          }
        })
        count += 1
        if (count == 0) {
          start = System.currentTimeMillis()
        }
        if (count > 0) {
          println(f"===== ${(count.toFloat / (System.currentTimeMillis() - start)) * 1000}%.1f fps")
        }
      }
    } else {
      while (true) {
        camIds.par.map(i => {
          var c = i - defaults('camFirst).asInstanceOf[Int]
          vcs(c).read(imgs(c))
          if (imgs(c) == null) {
            println(s"Read image is null")
          }
          if (imgs(c) != null && (imgs(c).rows == 0 || imgs(c).cols == 0)) {
            println(s"Image read from camera(i) has size == 0")
          }
          if (defaults('showWindow).asInstanceOf[Boolean]) {
            Util.show(imgs(c), s"cam: $i")
            if (saveDir != "") {
              val lastFilename = s"${Config.timeFormatter.print(DateTime.now)}"
              imwrite(s"$saveDir/d$i-$lastFilename.png", imgs(c))
              Thread.sleep(500)
            }
          }
        })
        count += 1
        if (count == 0) {
          start = System.currentTimeMillis()
        }
        if (count > 0) {
          println(f"===== ${(count.toFloat / (System.currentTimeMillis() - start)) * 1000}%.1f fps")
        }
      }
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
