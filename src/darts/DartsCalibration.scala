package darts

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

  checkCalibration
//  println(s"Camera 1 new calibration:\n${calibrateNewByXY("1")}")
//  println(s"Camera 2 new calibration:\n${calibrateNewByXY("2")}")
//  println(s"Camera 1 calibration:\n${calibrateByXY("1")}")
//  println(s"Camera 2 calibration:\n${calibrateByXY("2")}")

  def checkCalibration = {
    val camConf1 = new Config("1")
    val camConf2 = new Config("2")

    debug.setTo(Config.BlackMat)
    distancesFromBull map { dist => circle(debug, bullOffset, dist.toInt, Config.Cyan, 1, 8, 0) }

    readCalibrationCsv("1")

    val ab1 = calibPoints.map(c => {
      val x: Float = c.x
      val y: Float = c.y

      val x1: Float = camConf1.int("pos/leftx") + (x / camConf1.int("area/width")) * (camConf1.int("pos/rightx") - camConf1.int("pos/leftx"))
      val y1: Float = camConf1.int("pos/lefty") + (x / camConf1.int("area/width")) * (camConf1.int("pos/righty") - camConf1.int("pos/lefty"))

      // lastA, lastB is the parameters of the line goint from the camera through the point where the dart hit the board
      // the equation is: aX + B = y
      val lastA: Float = (y1 - camConf1.int("camy")) / (x1 - camConf1.int("camx"))
      val lastB: Float = y1 - lastA * x1
      (lastA, lastB, c.name)
    })

    readCalibrationCsv("2")

    val ab2 = calibPoints.map(c => {
      val x = c.x
      val y = c.y

      val x1 = camConf2.int("pos/leftx") + (x / camConf2.int("area/width")) * (camConf2.int("pos/rightx") - camConf2.int("pos/leftx"))
      val y1 = camConf2.int("pos/lefty") + (x / camConf2.int("area/width")) * (camConf2.int("pos/righty") - camConf2.int("pos/lefty"))

      // lastA, lastB is the parameters of the line going from the camera through the point where the dart hit the board
      // the equation is: aX + B = y
      val lastA = (y1 - camConf2.int("camy")) / (x1 - camConf2.int("camx"))
      val lastB = y1 - lastA * x1
      (lastA, lastB, c.name)
    })
    var c = 0
    (ab1 zip ab2).foreach(x => {
      val l1A: Float = x._1._1
      val l1B: Float = x._1._2
      val l2A: Float = x._2._1
      val l2B: Float = x._2._2
      val name = x._1._3
      var xc: Int = Math.round((l2B - l1B) / (l1A - l2A))
      var yc: Int = Math.round(xc * l1A + l1B)
      line(debug, new Point(xc+xOffset-5, yc+yOffset), new Point(xc+xOffset+5, yc+yOffset), Config.Green, 1, 8, 0)
      line(debug, new Point(xc+xOffset, yc+yOffset-5), new Point(xc+xOffset, yc+yOffset+5), Config.Green, 1, 8, 0)
      val point = new Point(xc.toInt, yc.toInt)
      val (mod, num) = DartsUtil.identifyNumber(point)
      println(s"name: $name num: $num mod: $mod")
      c = c + 1
    })
    Util.show(debug, s"Calibration check")

  }

  def calibrateNewByXY(camNum: String): String = {
    var result = ""
    println(s"-------------------- $camNum -------------------")

    debug.setTo(Config.BlackMat)
    distancesFromBull map { dist => circle(debug, bullOffset, dist.toInt, Config.Cyan, 1, 8, 0) }

    readCalibrationCsv(camNum)

    //    val res = getSumDiffByRatio2(new Point(933, -133), true)
    //    println(f"RES:      $res")

    val redGreenInters = guessCameraPosition
    var camPoint = new Point(redGreenInters._1.toInt, redGreenInters._2.toInt)
    println(s"StartPoint: ${camPoint.x}, ${camPoint.y}")
    val size = 10
    var prevMin = 9999999d
    var min = prevMin -1

    while(min < prevMin) {
      prevMin = min
      val res: Seq[(Int, Int, Double)] = for (i <- camPoint.x - size to camPoint.x + size; j <- camPoint.y - size to camPoint.y + size)
        yield (i, j, getSumDiffByRatio2(new Point(i, j), false))
      val minRes = res.filter(_._3 == res.map(_._3).min).head
      println(minRes)
      min = minRes._3
      camPoint = new Point(minRes._1, minRes._2)
    }
    circle(debug, new Point(camPoint.x + xOffset, camPoint.y + yOffset), 3, Config.Cyan, 2, LINE_AA, 0)
    Util.show(debug, s"Calibrating $camNum")
    //getSumDiffByRatio2(camPoint, true)
    // ------------------------------------------------
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

    var minDiff = 9999d
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
    * Calculates the error from the cameraPoint. Goes around the calibPoints and calculates the intersection
    * with a line perpendicular to (cameraPoint, bull) line.
    * @param cameraPoint
    * @param printDebug
    * @return
    */
  private def getSumDiffByRatio2(cameraPoint: Point, printDebug: Boolean):Double = {
    val angleToBull = CvUtil.getDegree(cameraPoint, bull)
    var leftPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat + 90, 400f)
    val rightPoint = CvUtil.rotatePoint(bull, angleToBull.toFloat - 90, 400f)

    if (printDebug) println(f"name;c.ratio;c.boardX;c.boardY;c.x;c.y;inters.x;inters.y;CvUtil.getDistance(leftPoint, inters);CvUtil.getDistance(inters, rightPoint)")
    val xcList: Seq[(Double,Double)] = calibPointsSorted.map(c => {
      val inters = CvUtil.lineIntersection(cameraPoint, new Point(c.boardX, c.boardY), leftPoint, rightPoint)
      if (printDebug) println(f"${c.name};${c.ratio};${c.boardX};${c.boardY};${c.x};${c.y};${inters.x};${inters.y};${CvUtil.getDistance(leftPoint, inters)};${CvUtil.getDistance(inters, rightPoint)}")
      (c.x.toDouble,CvUtil.getDistance(leftPoint, inters))
    }).toList

    val xMin = xcList.map(_._1).min
    val xSize = xcList.map(_._1).max - xMin
    val cMin = xcList.map(_._2).min
    val cSize = xcList.map(_._2).max - cMin

    val res = xcList.foldLeft(0d)((a, x) => a + CvUtil.sq((x._1 - xMin) - xSize * (x._2 - cMin)/cSize))
    //    println(f"res: $res")
    res
  }


  def calibrateByXY(camNum: String): String = {
    var result = ""
    println(s"-------------------- $camNum -------------------")

    calibPoints.clear()
    calibPointsSorted.clear()

    debug.setTo(Config.BlackMat)
    distancesFromBull map { dist => circle(debug, bullOffset, dist.toInt, Config.Cyan, 1, 8, 0) }

    readCalibrationCsv(camNum)

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
    * Reads calibration csv
    * @param camNum
    */
  private def readCalibrationCsv(camNum: String) = {
//    val dartFinder = new DartFinder(null, new Config(camNum))
    calibPoints.clear()
    calibPointsSorted.clear()
    var numCount = 0
    val num2coord = mutable.HashMap.empty[String, (Int, Int)]
    for (d <- 9 to 351 by 18) {
      line(debug, CvUtil.rotatePoint(bullOffset, d, distancesFromBull(1)), CvUtil.rotatePoint(bullOffset, d, distancesFromBull(5)), Config.Cyan, 1, 8, 0)
      val p: Point = CvUtil.rotatePoint(bull, d + 0.4f, distancesFromBull(5) - 1)
      circle(debug, new Point(p.x + xOffset, p.y + yOffset), 2, Config.Red, 2, 8, 0)
      num2coord.put(nums(numCount).toString, (p.x, p.y))
      numCount += 1
    }
    num2coord.put("bu", (bull.x, bull.y))

    val bufferedSource = io.Source.fromFile("calib_points.csv")
    for (line <- bufferedSource.getLines) {
      val c = line.split(";").map(_.trim)
      if (c(0) == s"CalibPoint$camNum") {
        calibPoints += CalibPoint(c(2).toDouble, c(3).toFloat, c(4).toFloat, num2coord.get(c(1)).get._1, num2coord.get(c(1)).get._2, c(1))
      }
    }
//    calibPoints map (c =>     println(s"CalibPoint${camNum};${c.ratio};${c.x};${c.y};${c.boardX};${c.boardY};${c.name}"))
    bufferedSource.close
    calibPointsSorted = calibPoints.sortBy(_.ratio)
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
    if (minDiff < pminDiff) {
      println(s"min diff: ${camPoint.x} ${camPoint.y} diff: $minDiff")
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
//    line(debug, p1, pe, color,i1, i2, i3)
    ((p2.x-p1.x).toFloat/(p2.y-p1.y), pe.x, pe.y)
  }
}
