package darts

import darts.CvUtil
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgcodecs.imread
import org.bytedeco.javacpp.opencv_imgproc.{circle, line, putText}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by vassdoki on 2017.03.17..
  *
  * This is not working right now.
  *
  */
object DartsCalibration extends App{
  val xOffset = 300 // debug image's offset
  val yOffset = 300 // debug image's offset

  val nums = List(13, 4, 18, 1, 20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, 6) // list of dart board segments in order
  val conversion = 1f
  val distancesFromBull = Array(14, 28, 174, 192, 284, 300).map(_*conversion) // distance from the bull
  val bull: Point = new Point(400, 400)  // the position of the bull on the destination
  val bullOffset: Point = new Point(xOffset + 400, yOffset + 400) // position of the bull on the debug image

  val debug = new Mat(1000, 1400, 16) // debug image


  var calibPoints = new mutable.ArrayBuffer[CalibPoint]()
  var calibPointsSorted = new mutable.ArrayBuffer[CalibPoint]()
  var viewCircles = new mutable.ArrayBuffer[ViewCircle]()
  var intersections = new mutable.ArrayBuffer[(Double, Double, Double, Double)]()
  var size = 1400

  case class CalibPoint(
                          ratio: Double // [0,1] the percentage of the seen point from the left side (0 leftmost point, 0.5 center)
                         ,x: Float, y: Float   // the x,y coordinate of the darts board, that was seen on the calibration image
                         ,boardX: Int, boardY: Int   // the x,y coordinate of the calibration point on the dart boards's coordinate system
                         ,name: String         // name of the segment [1..20, bu]
                       )
  case class ViewCircle(
                        x: Float, y: Float // the center of the circle
                       ,r: Double          // radius
                       ,angle: Double      // view angle from the camera to the two points used to calculate the view angle circle
                       )

  println(s"Camera 1 calibration:\n${calibrateByXY("1")}")
  println(s"Camera 2 calibration:\n${calibrateByXY("2")}")

  def calibrateByXY(camNum: String): String = {
    var result = ""
    println(s"-------------------- $camNum -------------------")

    calibPoints.clear()
    calibPointsSorted.clear()

    debug.setTo(Config.BlackMat)
    distancesFromBull map { dist => circle(debug, bullOffset, dist.toInt, Config.Cyan, 1, 8, 0) }

    readCalibrationImages(camNum)

    // guess a good starting point
    val redGreenInters = guessCameraPosition
    val redGreenIntersPoint = new Point(redGreenInters._1.toInt-1, redGreenInters._2.toInt)
    Util.show(debug, s"Calibrating guessed camera $camNum")

    var minDiff = 9999d
    var camPoint: Point = redGreenIntersPoint
    var continue = true
    while(continue) {
      // change the position of the camera until there is a better placement of it
      val x = getBestCamPoint(camPoint, minDiff, camNum)
      if (x._2 <= minDiff && x._1 != null) {
        minDiff = x._2
        camPoint = x._1
//        continue = false
//        println(f"minDiff: $minDiff%.6f    camera: ${camPoint.x} x ${camPoint.y}")
      } else {
//        println(f"minDiff: $minDiff%.6f    camera: ${camPoint.x} x ${camPoint.y} ez mar nem kisebb")
        continue = false
      }
    }

    // we have the position of the camera:
    circle(debug, new Point(camPoint.x + xOffset, camPoint.y + yOffset), 3, Config.Cyan, 2, LINE_AA, 0)

    // now find the yellow line based on the calibration information. this is very not precise
    val angleToBull = CvUtil.getDegree(camPoint, bull)
    val leftPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat + 90, 400f)
    val rightPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat - 90, 400f)
    var minDiffSum = 9999d
    var minSides: (Point, Point) = null
    calibPoints.foreach(c => {
      val inters = CvUtil.lineIntersection(camPoint, new Point(c.boardX, c.boardY), leftPoint, rightPoint)
      val bullDistance = CvUtil.getDistance(bull, inters)
      val fullDistance = bullDistance / Math.abs(0.5-c.ratio)
      val cleftPoint =  CvUtil.rotatePoint(bull, angleToBull.toFloat + 90, (fullDistance/2).toInt)
      val crightPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat - 90, (fullDistance/2).toInt)
      val diffSum = getSumDiffByPoints(camPoint, cleftPoint, crightPoint, false)
      if (diffSum < minDiffSum) {
        minDiffSum = diffSum
        minSides = (cleftPoint, crightPoint)
        result = s"<camx>${camPoint.x}</camx>\n<camy>${camPoint.y}</camy>\n<leftx>${cleftPoint.x}</leftx>\n<lefty>${cleftPoint.y}</lefty>\n<rightx>${crightPoint.x}</rightx>\n<righty>${crightPoint.y}</righty>\n"
      }
//      println(f"num: ${c.name}%2s sumDiffRatio: ${diffSum*100000}%8.2f " +
//        f"<leftx>${cleftPoint.x}</leftx><lefty>${cleftPoint.y}</lefty>" +
//        f"<rightx>${crightPoint.x}</rightx><righty>${crightPoint.y}</righty>  fullDistance: $fullDistance%.2f")

      circle(debug, new Point(cleftPoint.x + xOffset, cleftPoint.y + yOffset), 2, Config.Yellow, 2, LINE_AA, 0)
      circle(debug, new Point(crightPoint.x + xOffset, crightPoint.y + yOffset), 2, Config.Red, 2, LINE_AA, 0)
    })
    println(f"Smallest error: ${minDiffSum*100000}%.7f / 100000")

    minDiff = 9999
    val cSize = 7
    // fine tune the yellow line's position based on the best value from the calibration info
    // try every position in the cSize neighbourhood
    for(li <- -cSize to cSize; lj <- -cSize to cSize) {
      for(ri <- -cSize to cSize; rj <- -cSize to cSize) {
        val cleftPoint = new Point(minSides._1.x + li, minSides._1.y + lj)
        val crightPoint = new Point(minSides._2.x + ri, minSides._2.y + rj)
        val diffSum = getSumDiffByPoints(camPoint, cleftPoint, crightPoint, false)
        if (diffSum < minDiff) {
          minDiff = Math.min(minDiff, diffSum)
          result = s"<camx>${camPoint.x}</camx>\n<camy>${camPoint.y}</camy>\n<leftx>${cleftPoint.x}</leftx>\n<lefty>${cleftPoint.y}</lefty>\n<rightx>${crightPoint.x}</rightx>\n<righty>${crightPoint.y}</righty>\n"
        }
//        println(f"$li%3d $lj%3d $ri%3d $rj%3d sumDiffRatio: ${diffSum*100000}%8.2f")
      }
    }
    println(f"minDiff: ${minDiff*100000}%.2f / 100000")

    Util.show(debug, s"Calibrating $camNum")
    result
  }

  /**
    * Draw a line from every two calibration points. If the closer point looks left from the camera's point of view
    * then the line is green. Otherwise it is red. Rightest green and the highest red point's intersection is
    * the guessed camera location.
    * @return
    */
  def guessCameraPosition: (Float, Float) = {
    var maxRed = (-99999f, 0f, 0f, 0f, 0f) // (start x, y. endpoint x, y
    var maxGreen = (999999f, 0f, 0f, 0f, 0f)

    for(i <- 0 to calibPoints.size-2) {
      for(j <- i to calibPoints.size-1) {
        var p1 = calibPoints(i)
        var p2 = calibPoints(j)
        if (p1.boardY < p2.boardY) {
          p1 = calibPoints(j)
          p2 = calibPoints(i)
        }
        val color = if (p1.ratio > p2.ratio) {
          Config.Red
        } else {
          Config.Green
        }
        val endPoint = lineExtr(debug, new Point(xOffset+p1.boardX, yOffset+p1.boardY), new Point(xOffset+p2.boardX, yOffset+p2.boardY), 40f, color,1, 8, 0)
        if (color == Config.Red && endPoint._1 > maxRed._1) {
          // looking for x endpoint min
          maxRed = (endPoint._1, p1.boardX, p1.boardY, endPoint._2 - xOffset, endPoint._3 - yOffset)
        }
        if (color == Config.Green && endPoint._1 < maxGreen._1){
          // looking for x endpoint max
          maxGreen = (endPoint._1, p1.boardX, p1.boardY, endPoint._2 - xOffset, endPoint._3 - yOffset)
        }

      }
    }
    line(debug, new Point(maxRed._2.toInt+xOffset, maxRed._3.toInt+yOffset), new Point(maxRed._4.toInt+xOffset, maxRed._5.toInt+yOffset), Config.Red, 2, 8, 0)
    line(debug, new Point(maxGreen._2.toInt+xOffset, maxGreen._3.toInt+yOffset), new Point(maxGreen._4.toInt+xOffset, maxGreen._5.toInt+yOffset), Config.Green, 2, 8, 0)
    val redGreenInters = CvUtil.lineIntersection(maxRed._2, maxRed._3, maxRed._4, maxRed._5, maxGreen._2, maxGreen._3, maxGreen._4, maxGreen._5)
    circle(debug, new Point(redGreenInters._1.toInt + xOffset, redGreenInters._2.toInt + yOffset), 4, Config.Yellow, 2, LINE_AA, 0)
    redGreenInters
  }

  /**
    * Reads calibration image for 20 numbers and bull.
    * @param camNum
    */
  private def readCalibrationImages(camNum: String) = {
    val dartFinder = new DartFinder(null, new Config(camNum))
    var numCount = 0
    for (d <- 9 to 351 by 18) {
      line(debug, CvUtil.rotatePoint(bullOffset, d, distancesFromBull(1)), CvUtil.rotatePoint(bullOffset, d, distancesFromBull(5)), Config.Cyan, 1, 8, 0)
      val p: Point = CvUtil.rotatePoint(bull, d + 0.4f, distancesFromBull(5) - 1)
      calibPoints += readCalibImage(dartFinder, camNum, s"${Config.str("CALIB_DIR")}", s"${nums(numCount)}", p.x, p.y)
      circle(debug, new Point(xOffset + p.x, yOffset + p.y), 2, Config.Red, 2, 8, 0)
      numCount += 1
    }
    calibPoints += readCalibImage(dartFinder, camNum, s"${Config.str("CALIB_DIR")}", "bu", bull.x, bull.y)
    calibPointsSorted = calibPoints.sortBy(_.ratio)
    circle(debug, bullOffset, 2, Config.Red, 2, 8, 0)
  }

  /**
    * Find the camera position with the lowest error within the size neigbourhood
    * @param startPoint
    * @param pminDiff
    * @param camNum
    * @return the best point and the error value there
    */
  def getBestCamPoint(startPoint: Point, pminDiff: Double, camNum: String): (Point, Double) = {
    var minDiff = pminDiff
    var camPoint: Point = null
    var maxDiff = -9999d
    val size = 50
    for(i <- startPoint.x - size to startPoint.x + size) {
      for(j <- startPoint.y-size to startPoint.y + size) {
        val sumDiff = getSumDiffByRatio(new Point(i, j), false) //, leftPoint, rightPoint, distLeftRight)
        if (sumDiff < 200) circle(debug, new Point(i + xOffset, j + yOffset), 1, new Scalar(sumDiff, 30, 255,128), 1, LINE_AA, 0)
        if (maxDiff < sumDiff) maxDiff = sumDiff
        if (sumDiff < minDiff) {
          minDiff = sumDiff
          camPoint = new Point(i, j)
          circle(debug, new Point(i + xOffset, j + yOffset), 1, new Scalar(sumDiff, sumDiff, 255,128), 1, LINE_AA, 0)
//          println(s"$i $j diff: $sumDiff")
        }
      }
//      Util.show(debug, s"Calibrating $camNum")
    }
    (camPoint, minDiff)
  }

  /**
    * Calculates the error from the cameraPoint. Calculates the ratio of the intersection and the
    * leftP point. It should be the same as on the calibration image. The difference is the error.
    * @param cameraP
    * @param leftP
    * @param rightP
    * @param printDebug
    * @return
    */
  private def getSumDiffByPoints(cameraP: Point, leftP: Point, rightP: Point, printDebug: Boolean): Double = {
    val lrDistance = CvUtil.getDistance(leftP, rightP)
    var sumDiff = 0d
    calibPoints.foreach(c => {
      val inters = CvUtil.lineIntersection(cameraP, new Point(c.boardX, c.boardY), leftP, rightP)
      val ratio = CvUtil.getDistance(leftP, inters) / lrDistance
      if (printDebug) println(f"${c.name}%2s origRatio: ${c.ratio}%.7f calcRatio: $ratio%.7f    diff: ${ratio-c.ratio}    diff^2: ${CvUtil.sq(ratio - c.ratio)}%7f")
      sumDiff += CvUtil.sq(ratio - c.ratio)
    })
    sumDiff
  }

  /**
    * Calculates the error from the cameraPoint. Goes around the calibPoints and calculates the intersection
    * with a line perpendicular to (cameraPoint, bull) line.
    * @param cameraPoint
    * @param printDebug
    * @return
    */
  private def getSumDiffByRatio(cameraPoint: Point, printDebug: Boolean):Double = {
    val angleToBull = CvUtil.getDegree(cameraPoint, bull)
    var leftPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat + 90, 400f)
    val rightPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat - 90, 400f)
//    line(debug, new Point(leftPoint.x + xOffset, leftPoint.y + yOffset), new Point(rightPoint.x + xOffset, rightPoint.y + yOffset), Config.Cyan, 2, 8, 0)
//    val distLeftRight = CvUtil.getDistance(leftPoint, rightPoint)

//    println("---------------------------------")
    var sumDiff = 0d
    var prefOrigRatio = -1d
    var prevInters: Point = null
    var diffs = mutable.ArrayBuffer[(Double, Double)]() // orig ratio, current ratio
    var min = (9999d, 9999d)
    var max = (-9999d, -9999d)
    calibPointsSorted.foreach(c => {
      val inters = CvUtil.lineIntersection(cameraPoint, new Point(c.boardX, c.boardY), leftPoint, rightPoint)
      if (prevInters != null) {
        val distance = CvUtil.getDistance(prevInters, inters)
        diffs += Tuple2(c.ratio - prefOrigRatio, distance)
        if (printDebug) {
          println(f"${c.name} ${c.ratio - prefOrigRatio} $distance")
        }
        min = (Math.min(min._1, c.ratio - prefOrigRatio), Math.min(min._2, distance))
        max = (Math.max(max._1, c.ratio - prefOrigRatio), Math.max(max._2, distance))
      }
      prevInters = inters
      prefOrigRatio = c.ratio
    })
    sumDiff = diffs.map(d => CvUtil.sq(15*(d._1-min._1)/(max._1 - min._1) - 15*(d._2-min._2)/(max._2 - min._2))  ).sum
//    println(s"camPoint: ${cameraPoint.x} ${cameraPoint.y} sumDiff: $sumDiff")
    sumDiff
  }

  /**
    * Create a line from p1 and p2 point, and continue in p2's direction to extrapRate times
    * @param debug
    * @param p1
    * @param p2
    * @param extrapRate
    * @param color
    * @param i1
    * @param i2
    * @param i3
    * @return
    */
  def lineExtr(debug: Mat, p1: Point, p2: Point, extrapRate: Float, color: Scalar, i1: Int, i2: Int, i3: Int): (Float, Int, Int) = {
    val pe = new Point((p1.x + (p2.x-p1.x)*extrapRate).toInt, (p1.y + (p2.y-p1.y)*extrapRate).toInt)
    line(debug, p1, pe, color,i1, i2, i3)
    ((p2.x-p1.x).toFloat/(p2.y-p1.y), pe.x, pe.y)
  }

  /**
    * The filename is (path)/d(camName)-(name).jpg
    * @param camName Camera name in the config.xml
    * @param path directory containing the calibration images
    * @param name name of the darts board cell, where the dart is on the image. Top right corner of the tripple segment.
    * @param boardX X coordinate of the calibration image where the dart hit the board
    * @param boardY Y coordinate of the calibration image where the dart hit the board
    * @return CalibPoint
    */
  def readCalibImage(dartFineder: DartFinder, camName: String, path: String, name: String, boardX: Int, boardY: Int): CalibPoint = {
    val camConf = new Config(camName)
    val image: Mat = imread(s"$path/d$camName-$name.jpg")
    val mask: Mat = new Mat
    val (a, b) = dartFineder.findDartOnImage(image, mask)
    val (x, y) = dartFineder.getYintersection(a, b, camConf)
    val ratio: Double = x / image.cols
    CvUtil.releaseMat(mask)
    CvUtil.releaseMat(image)
    CalibPoint(ratio, x, y, boardX, boardY, name)
  }

}
