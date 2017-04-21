package darts

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

  val er9   = new Mat(9, 9, CV_8U, new Scalar(1d))
  val er7   = new Mat(7, 7, CV_8U, new Scalar(1d))
  val er5   = new Mat(5, 5, CV_8U, new Scalar(1d))
  val er3   = new Mat(3, 3, CV_8U, new Scalar(1d))

  var image = new Mat

  /**
    * ALWAYS_ON is a constant running version ONLY for one dart detection. Useful for testing.
    */
  object State extends Enumeration {
    val EMPTY, CHANGING, PRESTABLE, STABLE, REMOVING, ALWAYS_ON = Value
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
    * @return x (x, y) is where the dart hit the board on the camera image. y is always zeroy from the config.
    */
  def proc(debug: Mat, calib: CameraCalibrator, pImage: Mat): Float = {
    var image = new Mat
    //i2 = calib.remap(capture2.captureFrame(i2))
    if (pImage == null) {
      image = capture.captureFrame(image)
    } else {
      pImage.copyTo(image)
    }

    val debug: Mat = new Mat
    if (Config.bool("DEBUG_DART_FINDER")) image.copyTo(debug)

    val (a, b) = findDartOnImage(image, debug)
    var x = 0f
    var y = 0f
    var x1 = 0f
    var y1 = 0f
    if (a > -99999) {
      // x is the pixels from the left side of the image taken inside the area. this coordinate is from the side view
      val t = getYintersectionWithBoardSurface(a, b, camConf)
      x = t._1
      y = t._2
      val startPoint = new Point(camConf.int("area/x") + ((1 - b) / a).toInt, camConf.int("area/y") + 1)
      val endPoint = new Point(camConf.int("area/x") + x.toInt, camConf.int("area/y") + y.toInt)

      line(debug, startPoint, endPoint, Config.Cyan, 2, 4, 0)
      circle(debug, new Point(x.toInt, y.toInt + camConf.int("area/y")), 2, Config.Red, 2, LINE_AA, 0)

      //      line(debug, new Point(camConf.int("pos/leftx"), camConf.int("pos/lefty")),
      //        new Point(camConf.int("pos/rightx"), camConf.int("pos/righty")), Yellow, 1, 4, 0)
      //      circle(debug, new Point(camConf.int("pos/camx"), camConf.int("pos/camy")), 2, Yellow, 2, LINE_AA, 0)

      // x1, y1 represents the intersection point on the yellow line.
      // (the yellow line represents the camera view on the board's coordinate system)
      x1 = camConf.int("pos/leftx") + (x / camConf.int("area/width")) * (camConf.int("pos/rightx") - camConf.int("pos/leftx"))
      y1 = camConf.int("pos/lefty") + (x / camConf.int("area/width")) * (camConf.int("pos/righty") - camConf.int("pos/lefty"))

      // lastA, lastB is the parameters of the line goint from the camera through the point where the dart hit the board
      // the equation is: aX + B = y
      lastA = (y1 - camConf.int("camy")) / (x1 - camConf.int("camx"))
      lastB = y1 - lastA * x1
    }

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
    x
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

    val cNonZero = countNonZero(mask)

    val mState = (prevNonZeroCount, cNonZero) match {
      case (p, c) if (c < MinChange) => MaskState.EMPTY
      case (p, c) if (Math.abs(c - p) < MinChange) => MaskState.MIN_CHANGE
      case (p, c) if (Math.abs(c - p) >= MinChange) => MaskState.CHANGING
    }

    (state, mState) match {
      case (State.ALWAYS_ON, m) => {
        // stay here unless EMPTY
        val x = processStableMask(mask, cNonZero, srcOrigParam, debug)
        resA = x._1; resB = x._2
      }
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
    dilaErod(stableMask, er9, er5)
    stableNonZeroCount = newStableNonZeroCount
  }
  def saveNewStableMask = {
    prevMask.copyTo(newStableMask)
    dilaErod(newStableMask, er9, er5)
    newStableNonZeroCount = prevNonZeroCount
  }

  def processStableMask(pMask: Mat, cNonZero: Int, srcOrigParam: Mat, debug: Mat): (Float, Float) = {
    // this makes the image more robust. The small holes will disappera.
    // but if there are two darts close to eachother, it might fill the gap, that's no good
    var mask = new Mat(pMask.size, pMask.`type`())
    pMask.copyTo(mask)
    dilaErod(mask, er9, er9)

    val nonZero = new Mat
    if (stableNonZeroCount > 0) {
      // mask out the previous stable image, if that's not empty
      mask.setTo(Config.BlackMat, stableMask)
    }
    dilate(mask, mask, er3)
    findNonZero(mask, nonZero)
    CvUtil.releaseMat(mask)
    if (nonZero.rows > 0) {
      Util.lineFitForAB(nonZero, camConf.id)
    } else {
      (-99999f, -99999f)
    }
  }

  /**
    * Dilate and then erode the image to get rid of holes.
    * @param mask
    * @param d dilate matrix
    * @param e erode matrix
    */
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
  def getYintersectionWithBoardSurface(a: Float, b: Float, camConf2: Config): (Float, Float) = {
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
    // TODO: cvtColor is a slow method based on the profiling. Use it only on the are, not the whole image.
    cvtColor(srcOrigParam, srcOrig, CV_RGB2GRAY)
    val src2 = srcOrig(new Rect(camConf.int("area/x"), camConf.int("area/y"), camConf.int("area/width"), camConf.int("area/height")))
    threshold(src2, mask, camConf.int("threshold/min"), camConf.int("threshold/max"), THRESH_BINARY)
    bitwise_not(mask, mask)
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
