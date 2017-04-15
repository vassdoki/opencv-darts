package darts

import java.io.Serializable
import javax.swing.JFrame

import org.bytedeco.javacpp.indexer.{FloatIndexer, FloatRawIndexer}
import org.bytedeco.javacpp.opencv_core.{CV_32FC3, Mat, MatVector, Point3f}
import org.bytedeco.javacpp.opencv_imgproc.{DIST_L2, fitLine}
import org.bytedeco.javacv.CanvasFrame
import org.bytedeco.javacv.OpenCVFrameConverter.ToMat

import scala.collection.mutable

/**
  * Created by vassdoki on 2017.02.01.
  */
object Util {
    def lineFitForAB(m: Mat, o: String):(Float, Float) = {
      val lineFit = new Mat()
      Profile.start("07 - fitLine", o)
      fitLine(m, lineFit, DIST_L2, 2, 0.01, 0.01)
      Profile.end("07 - fitLine", o)
//      println(s"line ${lineFit.dims()} rows: ${lineFit.rows} cols: ${lineFit.cols()}")
      val irl = lineFit.createIndexer().asInstanceOf[FloatRawIndexer]

      //  for (i <- 0 until lineFit.rows()) {
      //    val x = irl.get(i, 0)
      //    println(s"line p: $x")
      //  }

      //val theMult = Math.max(result2.rows,result2.cols);
//      val theMult = 40
      val a = irl.get(1) / irl.get(0)
      val b = irl.get(3) - (irl.get(2) * a)
//      println(s"a: $a b: $b")
      (a, b)
    }

  /**
    * Convert to a Scala Seq collection.
    */
  def toSeq(matVector: MatVector): Seq[Mat] =
    for (i <- 0 until matVector.size.toInt) yield matVector.get(i)

  /**
    * Convert Scala sequence to MatVector.
    */
  def toMatVector(seq: Seq[Mat]): MatVector = new MatVector(seq: _*)

  val windows = mutable.Map.empty[String, CanvasFrame]
  def show(mat: Mat, title: String) {
    val canvas: CanvasFrame = if (windows.isDefinedAt(title)) {
      val x: Option[CanvasFrame] = windows.get(title)
      x.get
    } else {
      val c = new CanvasFrame(title, 1)
      windows.put(title, c)
      c.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
      c
    }
    val converter = new ToMat()
    canvas.showImage(converter.convert(mat))
  }

  /**
    * Convert a sequence of Point3D to a Mat representing a vector of Points3f.
    * Calling  `checkVector(3)` on the return value will return non-negative value indicating that it is a vector with 3 channels.
    */
  def toMatPoint3f(points: Seq[Point3f]): Mat = {
    // Create Mat representing a vector of Points3f
    val dest = new Mat(1, points.size, CV_32FC3)
    val indx = dest.createIndexer().asInstanceOf[FloatIndexer]
    for (i <- points.indices) {
      val p = points(i)
      indx.put(0, i, 0, p.x)
      indx.put(0, i, 1, p.y)
      indx.put(0, i, 2, p.z)
    }
    dest
  }

}
