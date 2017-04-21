package darts

import java.io.File

import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgcodecs._
import org.bytedeco.javacpp.opencv_videoio.VideoCapture
import org.bytedeco.javacpp.opencv_videoio._

import org.joda.time.DateTime

/**
  * Created by vassdoki on 2017.02.07..
  */
class CaptureDevice(val id: String) {
  var imageNumber = 0
  var lastFilename: String = null
  var lastOrigFilename: String = null

  val camConf = Config.getConfig(id)
  val capture: VideoCapture = if (Config.bool("USE_SAVED_IMAGES")) {
    null
  } else {
    new VideoCapture(camConf.int("videoDeviceNumber"))
  }
  var fileList = Seq.empty[File]

  if (Config.bool("USE_SAVED_IMAGES")) fileList = readFiles

  // TODO: check if this method is synchronized. paralel processing is not working on rasberry pi
  def captureFrame(f: Mat): Mat = {
    if (Config.bool("USE_SAVED_IMAGES")) {
      if (fileList.size == 0 && Config.bool("START_OVER")) {
        fileList = readFiles()
      }
      if (fileList.size == 0) {
        throw new Exception("No more file from CaptureFile")
      } else {
        val file = fileList.head
        lastOrigFilename = file.getName
        lastFilename = file.getName.substring(3, 26)
        fileList = fileList.tail
        f.release
        Profile.start("x - imread", id)
        val i = imread(file.getAbsolutePath)
        Profile.end("x - imread", id)
        i
      }
    } else {
      lastOrigFilename = s"d$id-${Config.timeFormatter.print(DateTime.now)}"
      lastFilename = s"${Config.timeFormatter.print(DateTime.now)}"
      imageNumber += 1
      capture.read(f)
      if (imageNumber == 1 && Config.bool("DEBUG_CAMERA_PROPERTIES")) {
        printCamProps
      }
      if (Config.bool("SAVE_CAPTURED")) {
        imwrite(s"${Config.str("OUTPUT_DIR")}/$lastOrigFilename.jpg", f)
      }
      f
    }
  }

  def release = {
    if (!Config.bool("USE_SAVED_IMAGES")) {
      capture.release()
    }
  }

  def printCamProps = {
    println(s"---------------------- cam: ${camConf.int("videoDeviceNumber")} --------------------")
    val camProps = List(
      (CAP_PROP_POS_MSEC, "CAP_PROP_POS_MSEC"),
      (CAP_PROP_POS_FRAMES, "CAP_PROP_POS_FRAMES"),
      (CAP_PROP_POS_AVI_RATIO, "CAP_PROP_POS_AVI_RATIO"),
      (CAP_PROP_FRAME_WIDTH, "CAP_PROP_FRAME_WIDTH"),
      (CAP_PROP_FRAME_HEIGHT, "CAP_PROP_FRAME_HEIGHT"),
      (CAP_PROP_FPS, "CAP_PROP_FPS"),
      (CAP_PROP_FOURCC, "CAP_PROP_FOURCC"),
      (CAP_PROP_FRAME_COUNT, "CAP_PROP_FRAME_COUNT"),
      (CAP_PROP_FORMAT, "CAP_PROP_FORMAT"),
      (CAP_PROP_MODE, "CAP_PROP_MODE"),
      (CAP_PROP_BRIGHTNESS, "CAP_PROP_BRIGHTNESS"),
      (CAP_PROP_CONTRAST, "CAP_PROP_CONTRAST"),
      (CAP_PROP_SATURATION, "CAP_PROP_SATURATION"),
      (CAP_PROP_HUE, "CAP_PROP_HUE"),
      (CAP_PROP_GAIN, "CAP_PROP_GAIN"),
      (CAP_PROP_EXPOSURE, "CAP_PROP_EXPOSURE"),
      (CAP_PROP_CONVERT_RGB, "CAP_PROP_CONVERT_RGB"),
      (CAP_PROP_WHITE_BALANCE_BLUE_U, "CAP_PROP_WHITE_BALANCE_BLUE_U"),
      (CAP_PROP_RECTIFICATION, "CAP_PROP_RECTIFICATION"),
      (CAP_PROP_MONOCHROME, "CAP_PROP_MONOCHROME"),
      (CAP_PROP_SHARPNESS, "CAP_PROP_SHARPNESS"),
      (CAP_PROP_AUTO_EXPOSURE, "CAP_PROP_AUTO_EXPOSURE"),
      (CAP_PROP_GAMMA, "CAP_PROP_GAMMA"),
      (CAP_PROP_TEMPERATURE, "CAP_PROP_TEMPERATURE"),
      (CAP_PROP_TRIGGER, "CAP_PROP_TRIGGER"),
      (CAP_PROP_TRIGGER_DELAY, "CAP_PROP_TRIGGER_DELAY"),
      (CAP_PROP_WHITE_BALANCE_RED_V, "CAP_PROP_WHITE_BALANCE_RED_V"),
      (CAP_PROP_ZOOM, "CAP_PROP_ZOOM"),
      (CAP_PROP_FOCUS, "CAP_PROP_FOCUS"),
      (CAP_PROP_GUID, "CAP_PROP_GUID"),
      (CAP_PROP_ISO_SPEED, "CAP_PROP_ISO_SPEED"),
      (CAP_PROP_BACKLIGHT, "CAP_PROP_BACKLIGHT"),
      (CAP_PROP_PAN, "CAP_PROP_PAN"),
      (CAP_PROP_TILT, "CAP_PROP_TILT"),
      (CAP_PROP_ROLL, "CAP_PROP_ROLL"),
      (CAP_PROP_IRIS, "CAP_PROP_IRIS"),
      (CAP_PROP_SETTINGS, "CAP_PROP_SETTINGS"),
      (CAP_PROP_BUFFERSIZE, "CAP_PROP_BUFFERSIZE"),
      (CAP_PROP_AUTOFOCUS, "CAP_PROP_AUTOFOCUS"))
    for (p <- camProps) {
      println(s"#${p._1} ${p._2} = ${capture.get(p._1)}")
    }
  }

  private def readFiles():Seq[File] = {
    val d = new File(Config.str("INPUT_DIR"))
    val listf = d.listFiles.filter(_.isFile)
    val filter = s"d${camConf.int("videoDeviceNumber")}"
    val filtered = listf.filter(_.getName.startsWith(filter))
    val res = Seq(filtered.sorted: _*)
    res
  }
}
