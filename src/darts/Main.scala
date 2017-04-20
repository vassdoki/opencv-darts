package darts

import java.io.File

import darts.CvUtil
import darts.DartsUtil
import org.bytedeco.javacpp.indexer.DoubleRawIndexer
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgcodecs.{IMREAD_COLOR, imread, imwrite}
import org.bytedeco.javacpp.opencv_imgproc.{circle, getPerspectiveTransform, _}

import scala.collection.mutable
import scala.collection.mutable.HashSet
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global



/**
  * Created by vassdoki on 2016.12.13..
  *
  */
object Main extends App{
  val Cyan    = new Scalar(255, 255, 0, 0)
  val Purple    = new Scalar(255, 0, 255, 0)
  val Yellow  = new Scalar(0, 255, 255, 0)
  val Red = new Scalar(0, 0, 255, 0)
  val Green = new Scalar(0, 255, 0, 0)
  val Black = new Scalar(0, 0, 0, 0)
  val BlackMat = new Mat(Black)
  val PrevMaskMat = new Mat(new Scalar(100, 100, 100, 0))

  var prevDartsCount1 = 0
  var prevDartsCount2 = 0

//  saveCalibratingImages
//  config
  test2

  def saveCalibratingImages = {
    val points = List(6, 13, 4, 18, 1, 20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, "bu", "finished")

    val capture1 = new CaptureDevice("1")
    val camConf1 = new Config("1")
    val capture2 = new CaptureDevice("2")
    val camConf2 = new Config("2")
    var i1 = new Mat
    var i2 = new Mat
    val maxCounter = 200
    var counter = maxCounter
    var savedImages = 0
    val debug2 = new Mat(800, 800, 16)
    while(true) {
      debug2.setTo(BlackMat)
      putText(debug2, s"$counter next target: ${points(savedImages)}", new Point(30, 100),
        FONT_HERSHEY_PLAIN, // font type
        2, // font scale
        Red, // text color (here white)
        3, // text thickness
        8, // Line type.
        false)

      //i1 = calib1.remap(capture1.captureFrame(i1))
      i1 = capture1.captureFrame(i1)
      i2 = capture2.captureFrame(i2)
      if (counter <= 0 && savedImages < points.size - 1) {
        imwrite(f"${Config.str("CALIB_DIR")}/d1-${points(savedImages)}.jpg", i1)
        imwrite(f"${Config.str("CALIB_DIR")}/d2-${points(savedImages)}.jpg", i2)
        savedImages += 1
        counter = maxCounter
      }
      drawKivagas(i1, camConf1)
      Util.show(i1, s"i1")
      //i2 = calib2.remap(capture2.captureFrame(i2))
      drawKivagas(i2, camConf2)
      Util.show(i2, s"i2")

//      drawCamDebug(debug2, camConf1)
//      drawCamDebug(debug2, camConf2)
      Util.show(debug2, s"cam config")
      counter -= 1
    }
    debug2.release()
  }

  def calibrateCamera(camNum: String, savedAlready: Boolean): CameraCalibrator = {
    // save 20 images
    var i = new Mat
    if (!savedAlready) {
      val capture = new CaptureDevice(camNum)
      val camConf = new Config(camNum)
      val imageToSave = 25
      var imageSaved = 0
      val skipBetween = 30
      var skipCount = skipBetween
      while (imageSaved < imageToSave) {
        i = capture.captureFrame(i)
        if (skipCount == 0) {
          imageSaved += 1
          imwrite(f"/home/vassdoki/darts/v3/calibration/calib-$camNum-$imageSaved%02d.jpg", i)
          skipCount = skipBetween
        }
        putText(i, s"Wait: $skipCount Saved: $imageSaved", new Point(30, 100),
          FONT_HERSHEY_PLAIN, // font type
          3, // font scale
          Red, // text color (here white)
          3, // text thickness
          8, // Line type.
          false)
        Util.show(i, s"image captured")
        skipCount -= 1
      }
    } else {
      i = imread(s"/home/vassdoki/darts/v3/calibration/calib-$camNum-10.jpg")
    }


    // calibrate

    // Generate file list
    val fileList = for (i <- (1 to 13).toSeq) yield new File(s"/home/vassdoki/darts/v3/calibration/calib-$camNum-%02d.jpg".format(i))

    // Create calibrator object
    val cameraCalibrator = new CameraCalibrator()

    // Add the corners from the chessboard
    println("Adding chessboard points from images...")
    val boardSize = new Size(9, 7)
    cameraCalibrator.addChessboardPoints(fileList, boardSize)

    // Calibrate camera
    println("Calibrating...")
    cameraCalibrator.calibrate(i.size())

    // Undistort
    //println("Undistorting...")
    //val undistorted = cameraCalibrator.remap(i)

    // Display camera matrix
    val m     = cameraCalibrator.cameraMatrix
    val mIndx = m.createIndexer().asInstanceOf[DoubleRawIndexer]
    println("Camera intrinsic: " + m.rows + "x" + m.cols)
    for (i <- 0 until 3) {
      for (j <- 0 until 3) {
        println("%7.2f  ".format(mIndx.get(i, j)))
      }
      println("-----")
    }

    //Util.show(undistorted, "Undistorted image.")

    cameraCalibrator
  }

  def config = {
    val capture1 = new CaptureDevice("1")
    val camConf1 = new Config("1")
    val capture2 = new CaptureDevice("2")
    val camConf2 = new Config("2")
    var i1 = new Mat
    var i2 = new Mat
    while(true) {
      //i1 = calib1.remap(capture1.captureFrame(i1))
      i1 = capture1.captureFrame(i1)
      drawKivagas(i1, camConf1)
      Util.show(i1, s"i1")
      //i2 = calib2.remap(capture2.captureFrame(i2))
      i2 = capture2.captureFrame(i2)
      drawKivagas(i2, camConf2)
      Util.show(i2, s"i2")
    }
  }

  def drawCamDebug(m: Mat, conf: Config) = {
    circle(m, new Point(conf.int("camx"), conf.int("camy")), 3, Red, 4, LINE_AA, 0)
    circle(m, new Point(conf.int("leftx"), conf.int("lefty")), 3, Red, 4, LINE_AA, 0)
    circle(m, new Point(conf.int("rightx"), conf.int("righty")), 3, Red, 4, LINE_AA, 0)
    line(m, new Point(conf.int("camx"), conf.int("camy")),
      new Point(conf.int("leftx"), conf.int("lefty")), Cyan, 1, 4, 0)
    line(m, new Point(conf.int("camx"), conf.int("camy")),
      new Point(conf.int("rightx"), conf.int("righty")), Cyan, 1, 4, 0)

  }

  def drawKivagas(m: Mat, conf: Config) = {
    line(m,
      new Point(conf.int("area/x"), conf.int("area/y")),
      new Point(conf.int("area/x") + conf.int("width"), (conf.int("area/y"))),
      Cyan, 1, 4, 0)
//    line(m,
//      new Point(10, 10),
//      new Point(conf.int("area/x") + conf.int("width"), (conf.int("area/y") - conf.int("area/height"))),
//      Green, 1, 4, 0)
    line(m,
      new Point(conf.int("area/x") + conf.int("width"), (conf.int("area/y"))),
      new Point(conf.int("area/x") + conf.int("width"), (conf.int("area/y") + conf.int("area/height"))),
      Cyan, 1, 4, 0)
    line(m,
      new Point(conf.int("area/x") + conf.int("width"), (conf.int("area/y") + conf.int("area/height"))),
      new Point(conf.int("area/x"), (conf.int("area/y") + conf.int("area/height"))),
      Cyan, 1, 4, 0)
    line(m,
      new Point(conf.int("area/x"), (conf.int("area/y") + conf.int("area/height"))),
      new Point(conf.int("area/x"), (conf.int("area/y"))),
      Cyan, 1, 4, 0)

    line(m,
      new Point(conf.int("area/x"), conf.int("area/zeroy")),
      new Point(conf.int("area/x") + conf.int("width"), (conf.int("area/zeroy"))),
      Red, 1, 4, 0)

    line(m,
      new Point(conf.int("area/x") + conf.int("width")/2, (conf.int("area/y"))),
      new Point(conf.int("area/x") + conf.int("width")/2, (conf.int("area/y") + conf.int("area/height"))),
      Yellow, 1, 4, 0)


  }

  def test2 = {
    Profile.start("01 - App")
    println(s"CPU cores: ${Runtime.getRuntime().availableProcessors()}")

    val camConf1 = new Config("1")
    val camConf2 = new Config("2")

    val dartFineder1 = new DartFinder(new CaptureDevice("1"), camConf1)
    val dartFineder2 = new DartFinder(new CaptureDevice("2"), camConf2)

    var i3 = new Mat
    var debug3:Mat = null

    val debug2 = new Mat(800, 800, 16)
    debug2.setTo(BlackMat)
    CvUtil.drawTable(debug2, Cyan, 2)
    CvUtil.drawNumbers(debug2, Yellow)

    val start = System.currentTimeMillis()
    var imgCount = 0
    var prevPoint: Point = null
    var prevTrPoint: Point = null

    val points = new HashSet[Point]

    try {
      while (true) {
        //      //                                      18(4)                               15(2)                                         7(16)                                            9(12)
        ///Thread.sleep(300)

        //      i3 = capture3.captureFrame(i3)
        //      Util.show(i3, s"i3")

        Profile.start("02 - dartFinder1")
        val f1 = Future {
          dartFineder1.proc(debug2, null)
        }
        Profile.end("02 - dartFinder1")
        Profile.start("02 - dartFinder2")
        val f2 = Future {
          dartFineder2.proc(debug2, null)
        }
        Profile.end("02 - dartFinder2")
        Profile.start("02 - await")
        val r1 = Await.result(f1, 2 second)
        val r2 = Await.result(f2, 2 second)

        Profile.end("02 - await")
        imgCount += 1
        if (dartFineder1.state == dartFineder1.State.EMPTY) prevDartsCount1 = 0
        if (dartFineder2.state == dartFineder1.State.EMPTY) prevDartsCount2 = 0
        if (
          (prevDartsCount1 < dartFineder1.dartsCount || prevDartsCount2 < dartFineder2.dartsCount)
            && (dartFineder1.state == dartFineder1.State.STABLE && dartFineder2.state == dartFineder2.State.STABLE)
            && (dartFineder1.dartsCount <= 3 && dartFineder2.dartsCount <= 3)
        ) {
          var xc = (dartFineder2.lastB - dartFineder1.lastB) / (dartFineder1.lastA - dartFineder2.lastA)
          var yc = xc * dartFineder1.lastA + dartFineder1.lastB
          val point = new Point(xc.toInt, yc.toInt)

          val (mod, num) = DartsUtil.identifyNumber(point)
          println(f"${(imgCount.toFloat / (System.currentTimeMillis() - start)) * 1000}%.1f fps num: $num mod: $mod   (${dartFineder1.dartsCount}[${dartFineder1.state}] ${dartFineder2.dartsCount}[${dartFineder2.state}])")
          prevDartsCount1 = dartFineder1.dartsCount
          prevDartsCount2 = dartFineder2.dartsCount

          if (Config.bool("SERVER_USE")) {
            val url = s"${Config.str("SERVER_URL")}?num=$num&modifier=$mod"
            scala.io.Source.fromURL(url).mkString
          }


          if (Config.bool("DEBUG_MAIN")) {
            debug2.setTo(BlackMat)
            CvUtil.drawTable(debug2, Cyan, 2)
            CvUtil.drawNumbers(debug2, Yellow)

            //          circle(debug2, new Point(x1.toInt, y1.toInt), 3, Red, 4, LINE_AA, 0)
            //          line(debug2, new Point(camConf1.int("camx"), camConf1.int("camy")), new Point(50, (50 * a1 + b1).toInt), Cyan, 1, 4, 0)
            //          line(debug2, new Point(camConf1.int("leftx"), camConf1.int("lefty")), new Point(camConf1.int("rightx"), camConf1.int("righty")), Yellow, 1, 4, 0)
            //
            //          circle(debug2, new Point(x2.toInt, y2.toInt), 3, Red, 4, LINE_AA, 0)
            //          line(debug2, new Point(camConf2.int("camx"), camConf2.int("camy")), new Point(750, (750 * a2 + b2).toInt), Cyan, 1, 4, 0)
            //          line(debug2, new Point(camConf2.int("leftx"), camConf2.int("lefty")), new Point(camConf2.int("rightx"), camConf2.int("righty")), Yellow, 1, 4, 0)

            points.add(point)
            if (prevPoint != null) circle(debug2, prevPoint, 2, Green, 2, LINE_AA, 0)
            points.foreach(p => circle(debug2, p, 1, Green, 1, LINE_AA, 0))
            circle(debug2, point, 4, Red, 5, LINE_AA, 0)
            prevPoint = point

            putText(debug2, s"$num x $mod", new Point(20, 90),
              FONT_HERSHEY_PLAIN, // font type
              4, // font scale
              Yellow, // text color (here white)
              4, // text thickness
              8, // Line type.
              false)
            putText(debug2, f"${(imgCount.toFloat / (System.currentTimeMillis() - start)) * 1000}%.1f fps", new Point(400, 30),
              FONT_HERSHEY_PLAIN, // font type
              2, // font scale
              Red, // text color (here white)
              2, // text thickness
              8, // Line type.
              false)
            putText(debug2, s"${xc.toInt} x ${yc.toInt}", new Point(10, 30),
              FONT_HERSHEY_PLAIN, // font type
              3, // font scale
              Red, // text color (here white)
              3, // text thickness
              8, // Line type.
              false)

            Util.show(debug2, s"debug2")
          }
        }
      }
    }catch {
      case e: Exception => {
        println(e)
        e.printStackTrace()
      }
    }
    Profile.end("01 - App")
    Profile.print
  }




}
