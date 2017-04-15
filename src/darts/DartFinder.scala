package darts

import darts.CvUtil
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc._
import org.bytedeco.javacpp.opencv_imgcodecs._

/**
  * Created by vassdoki on 2017.03.27..
  */
class DartFinder(val capture: CaptureDevice, val camConf: Config) {
  val MinChange = 40
  val MinStable = 3

  var prevMask: Mat = new Mat(camConf.int("area/height"), camConf.int("area/width"), CV_8U)
  var prevNonZeroCount = 0
  var stableMask: Mat = new Mat(camConf.int("area/height"), camConf.int("area/width"), CV_8U)
  var stableNonZeroCount = 0
  var newStableMask: Mat = new Mat(camConf.int("area/height"), camConf.int("area/width"), CV_8U)
  var newStableNonZeroCount = 0

  var state = State.EMPTY
  var stableCount = 0
  var dartsCount = 0
  var lastA = 0f // in Ax + B = y. line from tha camera on the board
  var lastB = 0f

  val er7   = new Mat(7, 7, CV_8U, new Scalar(1d))
  val er5   = new Mat(5, 5, CV_8U, new Scalar(1d))
  val er3   = new Mat(3, 3, CV_8U, new Scalar(1d))

  var image = new Mat

  object State extends Enumeration {
    val EMPTY, CHANGING, PRESTABLE, STABLE, REMOVING = Value
  }
  object MaskState extends Enumeration {
    val EMPTY = Value // the mask is empty => always goes to EMPTY state
    val CHANGING = Value // has more changed pixels => always go to CHANGING state

    val MIN_CHANGE = Value // prev and curr is almost the same

  }

  case class PrevThrow(
                        noneZero: Int,
                        mask: Mat,
                        num: Int,
                        mod: Int
                      )

  /**
    * Read an image from the camera. Find the dart on it, find its intersection with the dart board.
    *
    * @param debug
    * @param calib
    * @return (x1, y1, x, y): (x1, y1) is the calculated position on the dart board. (x, y) is where the
    *         dart hit the board on the camera image
    */
  def proc(debug: Mat, calib: CameraCalibrator): Float = {
    Profile.start("03 - proc", camConf.id)
    var image = new Mat
    //i2 = calib.remap(capture2.captureFrame(i2))
    Profile.start("04 - capture", camConf.id)
    image = capture.captureFrame(image)
    Profile.end("04 - capture", camConf.id)

    Profile.start("04 - image copy", camConf.id)
    val debug: Mat = new Mat
    if (Config.bool("DEBUG_DART_FINDER")) image.copyTo(debug)
    Profile.start("04 - image copy", camConf.id)

    Profile.start("04 - findDartOnImage", camConf.id)
    val (a, b) = findDartOnImage(image, debug)
    Profile.end("04 - findDartOnImage", camConf.id)
    Profile.start("04 - if", camConf.id)
    if (a > -99999) {
      val (x, y) = getYintersection(a, b, camConf)
      val startPoint = new Point(camConf.int("area/x") + ((1 - b) / a).toInt, camConf.int("area/y") + 1)
      val endPoint = new Point(camConf.int("area/x") + x.toInt, camConf.int("area/y") + y.toInt)

      line(debug, startPoint, endPoint, Config.Cyan, 2, 4, 0)
      circle(debug, new Point(x.toInt, y.toInt + camConf.int("area/y")), 2, Config.Red, 2, LINE_AA, 0)

      //      line(debug, new Point(camConf.int("pos/leftx"), camConf.int("pos/lefty")),
      //        new Point(camConf.int("pos/rightx"), camConf.int("pos/righty")), Yellow, 1, 4, 0)
      //      circle(debug, new Point(camConf.int("pos/camx"), camConf.int("pos/camy")), 2, Yellow, 2, LINE_AA, 0)

      val x1 = camConf.int("pos/leftx") + (x / camConf.int("area/width")) * (camConf.int("pos/rightx") - camConf.int("pos/leftx"))
      val y1 = camConf.int("pos/lefty") + (x / camConf.int("area/width")) * (camConf.int("pos/righty") - camConf.int("pos/lefty"))

      (x1, y1, x, y)
      lastA = (y1 - camConf.int("camy")) / (x1 - camConf.int("camx"))
      lastB = y1 - lastA * x1
    }
    Profile.end("04 - if", camConf.id)

    if (Config.bool("DEBUG_DART_FINDER")) {
      val dmask = new Mat(prevMask.size(), prevMask.`type`())

      cvtColor(prevMask, dmask, CV_GRAY2BGR)
      dmask.copyTo(debug(new Rect(0, debug.rows - prevMask.rows, prevMask.cols, prevMask.rows)))

      dmask.setTo(Config.BlackMat, stableMask)
      dilate(dmask, dmask, er3)
      dmask.copyTo(debug(new Rect(0, debug.rows - (prevMask.rows * 2), prevMask.cols, prevMask.rows)))

      cvtColor(stableMask, dmask, CV_GRAY2BGR)
      dmask.copyTo(debug(new Rect(0, debug.rows - (prevMask.rows * 3), prevMask.cols, prevMask.rows)))

      Main.drawKivagas(debug, camConf)

      putText(debug, s"prevNonZero: $prevNonZeroCount state: $state dartsCount: $dartsCount stableCount: $stableCount", new Point(20, 20),
        FONT_HERSHEY_PLAIN, // font type
        1, // font scale
        Config.Red, // text color (here white)
        1, // text thickness
        1, // Line type.
        false)
      putText(debug, f"a: $a%f b: $b%f", new Point(20, 50),
        FONT_HERSHEY_PLAIN, // font type
        1, // font scale
        Config.Red, // text color (here white)
        1, // text thickness
        1, // Line type.
        false)

      Util.show(debug, s"debug ${camConf.id}")
      CvUtil.releaseMat(dmask)
    }

    CvUtil.releaseMat(debug)
//    CvUtil.releaseMat(image)
    Profile.end("03 - proc", camConf.id)
    lastA
  }

  /**
    * Returns the dart found on the image by (a, b) in the equation: y = ax + b
    * (-99999,-99999) if not found
    * @param srcOrigParam
    * @return
    */
  def findDartOnImage(srcOrigParam: Mat, debug: Mat):(Float, Float) = {
    var (resA, resB) = (-99999f,-99999f)

    val mask: Mat = new Mat
    getNewMask(srcOrigParam, mask)

    Profile.start("06 - countNonZero", camConf.id)
    val cNonZero = countNonZero(mask)
    Profile.end("06 - countNonZero", camConf.id)

    val mState = (prevNonZeroCount, cNonZero) match {
      case (p, c) if (c < MinChange) => MaskState.EMPTY
      case (p, c) if (Math.abs(c - p) < MinChange) => MaskState.MIN_CHANGE
      case (p, c) if (Math.abs(c - p) >= MinChange) => MaskState.CHANGING
    }

    (state, mState) match {
      case (State.EMPTY, MaskState.MIN_CHANGE) => {
        // if empty, reset the states
        resetState
        state = State.EMPTY
      }
      case (s, MaskState.EMPTY) => {
        // if empty, reset the states
        resetState
        state = State.EMPTY
      }
      case (State.STABLE, MaskState.CHANGING) => {
        saveStableMask
        stableCount = 0
        state = State.CHANGING
      }
      case (s, MaskState.CHANGING) => {
        stableCount = 0
        state = State.CHANGING
      }

      case (State.REMOVING, m) => {
        // stay here unless EMPTY
        state = State.REMOVING
      }
      case (State.CHANGING, MaskState.MIN_CHANGE) if (stableCount < MinStable) => {
        stableCount += 1
      }
      case (State.CHANGING, MaskState.MIN_CHANGE) if (stableCount >= MinStable) => {
        state = State.STABLE
        dartsCount += 1
        saveNewStableMask
        val x = processStableMask(mask, cNonZero, srcOrigParam, debug)
        resA = x._1; resB = x._2
      }
      case (State.STABLE, MaskState.MIN_CHANGE) => {
        stableCount += 1
//        val x = processStableMask(mask, cNonZero, srcOrigParam, debug)
//        resA = x._1; resB = x._2
      }
    }
//    println(s"cNonZero: $cNonZero prevZero: $prevNonZeroCount mState: $mState new state: $state")

    prevNonZeroCount = cNonZero
    CvUtil.releaseMat(prevMask)
    prevMask = mask

    (resA, resB)
  }

  def saveStableMask = {
    newStableMask.copyTo(stableMask)
    dilaErod(stableMask, er7, er5)
    stableNonZeroCount = newStableNonZeroCount
  }
  def saveNewStableMask = {
    prevMask.copyTo(newStableMask)
    dilaErod(newStableMask, er7, er5)
    newStableNonZeroCount = prevNonZeroCount
  }
  def processStableMask(pMask: Mat, cNonZero: Int, srcOrigParam: Mat, debug: Mat): (Float, Float) = {
    // this makes the image more robust. The small holes will disappera.
    // but if there are two darts close to eachother, it might fill the gap, that's no good
    Profile.start("05 - mask.Copy", camConf.id)
    var mask = new Mat(pMask.size, pMask.`type`())
    pMask.copyTo(mask)
    Profile.end("05 - mask.Copy", camConf.id)
    Profile.start("05 - dilaErode", camConf.id)
    dilaErod(mask, er7, er7)
    Profile.end("05 - dilaErode", camConf.id)

    val nonZero = new Mat
    if (stableNonZeroCount > 0) {
      // mask out the previous stable image, if that's not empty
      mask.setTo(Config.BlackMat, stableMask)
    }
    dilate(mask, mask, er3)
    Profile.start("05 - findNonZero", camConf.id)
    findNonZero(mask, nonZero)
    Profile.end("05 - findNonZero", camConf.id)
    CvUtil.releaseMat(mask)
    if (nonZero.rows > 0) {
      Util.lineFitForAB(nonZero, camConf.id)
    } else {
      (-99999f, -99999f)
    }
  }

  def dilaErod(mask: Mat, d: Mat, e: Mat) = {
//    Util.show(mask, s"before XXX ${camConf.id}")
    if (d != null) dilate(mask, mask, d)
    if (e != null) erode(mask, mask, e)
//    Util.show(mask, s"after XXX ${camConf.id}")
  }
  /**
    * Given the line (ax + b) from the image, returns where the dart hit the board on the image (x, y)
    * @param a
    * @param b
    * @param camConf2
    * @return
    */
  def getYintersection(a: Float, b: Float, camConf2: Config): (Float, Float) = {
    val y = camConf2.int("area/zeroy") - camConf2.int("area/y")
    val x = ((y - b) / a)
    (x, y)
  }

  /**
    * Cut the part of the image specified in the config.xml. Threshold it to remove the background.
    * @param srcOrigParam
    * @param mask
    * @return
    */
  def getNewMask(srcOrigParam: Mat, mask: Mat): Mat = {
    val srcOrig = new Mat
    Profile.start("07 - cvtColor", camConf.id)
    cvtColor(srcOrigParam, srcOrig, CV_RGB2GRAY)
    Profile.end("07 - cvtColor", camConf.id)
    val src2 = srcOrig(new Rect(camConf.int("area/x"), camConf.int("area/y"), camConf.int("area/width"), camConf.int("area/height")))
    Profile.start("07 - threshold", camConf.id)
    threshold(src2, mask, camConf.int("threshold/min"), camConf.int("threshold/max"), THRESH_BINARY)
    Profile.end("07 - threshold", camConf.id)
    Profile.start("07 - bitwise_not", camConf.id)
    bitwise_not(mask, mask)
    Profile.end("07 - bitwise_not", camConf.id)
    srcOrig.release
    src2.release
    mask
  }

  def resetState = {
    prevMask.setTo(Config.BlackMat)
    prevNonZeroCount = 0
    stableMask.setTo(Config.BlackMat)
    stableNonZeroCount = 0

    stableCount = 0
    dartsCount = 0

    state = State.EMPTY
  }

}
