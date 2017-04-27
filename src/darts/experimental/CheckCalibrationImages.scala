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
  val camConf = new Config("1")
  val d = new File("/home/vassdoki/darts/v3/d-calibration/jo-kalibracio")
  val listf = d.listFiles.filter(_.isFile)
  val filter = s"d${camConf.int("videoDeviceNumber")}"
  val filtered = listf.filter(_.getName.startsWith(filter))


  val capture1 = new CaptureDevice("1")
  val camConf1 = new Config("1")
  val dartFinder1 = new DartFinder(capture1, camConf1)
  dartFinder1.state = dartFinder1.State.ALWAYS_ON

  for(file <- filtered.sorted) {
    var i = imread(s"${file.getPath}")
    dartFinder1.proc(i)
    val d = dartFinder1.debugLastProc
    Util.show(d, s"test")
    imwrite(s"/home/vassdoki/darts/v3/t/${file.getName}", d)
//    Thread.sleep(10)
    CvUtil.releaseMat(d)
  }

}
