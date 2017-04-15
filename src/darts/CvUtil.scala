package darts

import java.awt.image.BufferedImage

import org.bytedeco.javacpp.indexer.{DoubleIndexer, FloatIndexer, UByteRawIndexer}
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc._
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter.ToMat

/**
 * Created by vassdoki on 2016.08.11..
 */
object CvUtil {
  val openCVConverter = new ToMat()
  val java2DConverter = new Java2DFrameConverter()

  def toMatPoint2f(points: Seq[Point2f]): Mat = synchronized  {
    // Create Mat representing a vector of Points3f
    val dest = new Mat(1, points.size, CV_32FC2)
    val indx = dest.createIndexer().asInstanceOf[FloatIndexer]
    for (i <- points.indices) {
      val p = points(i)
      indx.put(0, i, 0, p.x)
      indx.put(0, i, 1, p.y)
    }
    require(dest.checkVector(2) >= 0)
    dest
  }

  def toMatArrayFloat(f: Array[Float]): Mat =  synchronized {
    // Create Mat representing a vector of Points3f
    val dest = new Mat(1, f.size, CV_32F)
    val indx = dest.createIndexer().asInstanceOf[FloatIndexer]
    for (i <- f.indices) {
      val p = f(i)
      indx.put(0, i, 0, p)
    }
    dest
  }


  def drawTable(src: Mat, color: Scalar, lineWidth: Int = 2) =  synchronized {
    val bull: Point = new Point(Config.bull.x, Config.bull.y)

    Config.distancesFromBull map { dist => circle(src, bull, dist.toInt, color, lineWidth, 8, 0) }
    for (d <- 9 to 351 by 18) {
      line(src, rotatePoint(bull, d, Config.distancesFromBull(1)), rotatePoint(bull, d, Config.distancesFromBull(5)), color,lineWidth, 8, 0)
    }
  }

  def drawNumbers(src: Mat, color: Scalar) =  synchronized {
    val bull: Point = new Point(Config.bull.x, Config.bull.y)

    var i = 0
    for (d <- 9 to 351 by 18) {
      putText(src, f"${Config.nums(i).toInt}", rotatePoint(bull, d-9, (Config.distancesFromBull(5)*1.1).toInt),
        FONT_HERSHEY_PLAIN, // font type
        1, // font scale
        color, // text color (here white)
        3, // text thickness
        8, // Line type.
        false)
      i += 1
    }
  }

  def drawCross(src: Mat, x: Int, y: Int, colorNum: Int = 0, size: Int = 10) =  synchronized {
    val color = List(
      new Scalar(51,255,255,0),new Scalar(255,51,255,0),new Scalar(255,255,51,0)
    )
    line(src, new Point(x - size,y), new Point(x+size,y), color(colorNum), 2, 8, 0)
    line(src, new Point(x,y - size), new Point(x,y+size), color(colorNum), 2, 8, 0)
  }

  def rotatePoint(c: Point, degree: Float, radius: Float): Point = synchronized  {
    val cos = Math.cos(Math.PI * degree / 180)
    val sin = Math.sin(Math.PI * degree / 180)
    new Point((c.x + cos * radius).toInt, (c.y - sin * radius).toInt)
  }

  def toBufferedImage(mat: Mat): BufferedImage =  synchronized {
    try {
      if (openCVConverter == null || java2DConverter == null) {
        println("CONVERTER NULL")
        null
      } else {
        if (mat == null) {
          println("MAT NULL??")
          null
        } else {
          java2DConverter.convert(openCVConverter.convert(mat))
        }
      }
    }catch {
      case e: Exception => {
        println("ToBufferedImage EXCEPTION")
        e.printStackTrace()
        null
      }
    }
  }

  def getDistanceFromBull(p: Point): Double =  synchronized {
    Math.sqrt(sq(Config.bull.x - p.x) + sq(Config.bull.y - p.y))
  }
  def getDistance(p: Point, p2: Point): Double =  synchronized {
    Math.sqrt(sq(p2.x - p.x) + sq(p2.y - p.y))
  }
  def getDistance(x1:Int, y1: Int, x2:Int, y2: Int): Double =  synchronized {
    Math.sqrt(sq(x2 - x1) + sq(y2 - y1))
  }
  def getDistance(x1:Float, y1: Float, x2:Float, y2: Float): Double =  synchronized {
    Math.sqrt(sq(x2 - x1) + sq(y2 - y1))
  }
  def getDistance(x1:Double, y1: Double, x2:Double, y2: Double): Double =  synchronized {
    Math.sqrt(sq(x2 - x1) + sq(y2 - y1))
  }


  def getDegreeFromBull(p: Point) = getDegree(new Point(Config.bull.x, Config.bull.y), p)

  def getDegree(bull: Point, p: Point) : Double = {
    val x = p.x - bull.x
    val y = bull.y - p.y
    var v = 180 * Math.atan2(y, x) / Math.PI
    if (v > 180) {
      v = 180
    }
    if (v < -180) {
      v = -180
    }
    if (v < 0) {
      v += 360
    }
    if (v == 0) {
      //Log.i(TAG, "arch: y: " + y + " x: " + x + " archtan: " + v);
    }
    v
  }
  def getDegree180(p1: Point, p2: Point): Double = {
    val d = getDegree(p1, p2)
    if (d > 180) {
      d - 180
    } else {
      d
    }
  }

  def dec2rad(degree: Double) : Double = Math.toRadians(degree) //degree * (Math.PI / 180)
  def rad2dec(rad: Double) : Double = Math.toDegrees(rad) //rad * (180 / Math.PI)

  /** y1 = ax + c  y2 = bx + d */
  def lineIntersection(a: Double, c: Int, b: Double, d: Int) : Point = {
    val x = (d - c) / (a - b)
    val y = a * x + c
    new Point(x.toInt, y.toInt)
  }
  def lineIntersection(a: Float, c: Float, b: Float, d: Float) : Point = {
    val x = (d - c) / (a - b)
    val y = a * x + c
    new Point(x.toInt, y.toInt)
  }
  def lineIntersection(a: Point, b: Point, c: Point, d: Point) : Point = {
    val r = lineIntersection(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)
    new Point(r._1.toInt, r._2.toInt)
  }
  def lineIntersection(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) : (Float, Float) = {
    (
      ((x1*y2 - y1*x2)*(x3-x4) - (x1 - x2)*(x3*y4 - y3*x4)) / ( (x1 - x2)*(y3 - y4) - (y1 - y2)*(x3-x4) )
      ,
      ((x1*y2 - y1*x2)*(y3-y4) - (y1 - y2)*(x3*y4 - y3*x4)) / ( (x1 - x2)*(y3 - y4) - (y1 - y2)*(x3-x4) )
    )
  }

  def getDistanceFromBull(p: CvPoint): Double = {
    Math.sqrt(sq(Config.bull.x - p.x) + sq(Config.bull.y - p.y))
  }

  def getPointDistanceFromLine(p1: Point, p2: Point, a: Point): Double = {
    Math.abs( ( (p2.y-p1.y)*a.x - (p2.x-p1.x)*a.y + p2.x*p1.y - p2.y*p1.x ) / Math.sqrt( sq(p2.y-p1.y) + sq(p2.x-p2.x)) )
  }

  def sq(a: Double): Double = a * a

  def releaseMat(m: Mat): Unit = {
    if (m != null) {
      m.release()
    }
  }

  def findTopWhite(m: Mat, xOffset: Int, yOffset: Int): (Int, Int) = {
    val sI: UByteRawIndexer = m.createIndexer()
    var j = 0
    val w = m.cols
    val h = m.rows
    var x = 0
    var y = 0
    var color = 0
    var resJ = 0

    //val debug = new Mat(m.rows, m.cols,CV_8UC3)

    while(y < h  && x < w && resJ == 0) { //  && color < 50
      color = sI.get(y, x, 0) & 0xFF
      if (resJ == 0 && color > 100) {
        resJ = j
      }
      //      if (color == 0) {
      //        circle(debug, new Point(x, y), 1, Config.COLOR_BLUE, 1, 8, 0)
      //      } else {
      //        if (color < 100) {
      //          circle(debug, new Point(x, y), 1, Config.COLOR_RED, 1, 8, 0)
      //        } else {
      //          circle(debug, new Point(x, y), 1, Config.COLOR_YELLOW, 1, 8, 0)
      //        }
      //      }
      j += 1
      x = j % w
      y = j / w
    }

    //imwrite(s"${Config.OUTPUT_DIR}/${pImgName}-$imageCount-box-a-source-$xOffset-$w-$h.jpg", m)
    //imwrite(s"${Config.OUTPUT_DIR}/${pImgName}-$imageCount-box-b.jpg-$xOffset-$w-$h.jpg", debug)

    (resJ % w + xOffset, resJ / w + yOffset)

  }


}
