package experimental

import java.io.File

import darts._
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, Mat, Point}
import org.bytedeco.javacpp.opencv_imgcodecs.{imread, imwrite}
import org.bytedeco.javacpp.opencv_imgproc.putText

/**
  * Created by vassdoki on 2017.04.27..
  */
object CheckCalibrationImages extends App{
  check("1")
  check("2")

  def check(camId: String) {
    val camConf = new Config(camId)
    val d = new File("/home/vassdoki/darts/v3/d-calibration/jo-kalibracio")
    val listf = d.listFiles.filter(_.isFile)
    val filter = s"d${camConf.int("videoDeviceNumber")}"
    val filtered = listf.filter(_.getName.startsWith(filter))


    val capture = new CaptureDevice(camId)
    val dartFinder = new DartFinder(capture, camConf)
    dartFinder.state = dartFinder.State.ALWAYS_ON

    for (file <- filtered.sorted) {
      var i = imread(s"${file.getPath}")
      val x = dartFinder.proc(i)

      val name = file.getName.substring(3).replace(".jpg", "")
      println(f"CalibPoint$camId;$name;${x/camConf.int("area/width")}%15f;$x;${camConf.int("area/zeroy")}")

      val d = dartFinder.debugLastProc
      Util.show(d, s"test")
      imwrite(s"/home/vassdoki/darts/v3/t/${file.getName}", d)
      //    Thread.sleep(10)
      CvUtil.releaseMat(d)
    }
  }

}
