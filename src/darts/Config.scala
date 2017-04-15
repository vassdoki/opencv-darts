package darts

import org.joda.time.format.DateTimeFormat

import scala.util.parsing.json.JSON
import scala.xml.{NodeSeq, XML}
import java.io.File

import org.bytedeco.javacpp.opencv_core.{Mat, Point, Scalar}


class Config(val id: String) {
  def int(path: String):Int = Config.int(id, path)
  def bool(path: String):Boolean = Config.bool(id, path)
  def str(path: String):String = Config.str(id, path)

}

object Config {
  val Cyan    = new Scalar(255, 255, 0, 0)
  val Blue    = new Scalar(255, 100, 0, 0)
  val Purple    = new Scalar(255, 0, 255, 0)
  val Yellow  = new Scalar(0, 255, 255, 0)
  val Red = new Scalar(0, 0, 255, 0)
  val Green = new Scalar(0, 255, 0, 0)
  val Black = new Scalar(0, 0, 0, 0)
  val BlackMat = new Mat(Black)
  val WhiteMat = new Mat(new Scalar(255, 255, 255, 0))

  val conversion = 1f
  val nums = List(6, 13, 4, 18, 1, 20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10).map(_/conversion)
  val distancesFromBull = Array(14, 28, 174, 192, 284, 300).map(_/conversion)
  val bull = new Point((400/conversion).toInt, (400/conversion).toInt)


  val timeFormatter = DateTimeFormat.forPattern("Y-MMM-d_H-mm_ss-SS");

  val file = new File("config.xml")
  if (!file.isFile) {
    println("config.xml not found. To start with, copy config-sample.xml from the project.")
    System.exit(2)
  }
  var lastModified = file.lastModified()
  var xml = XML.loadFile("config.xml")
  var x: NodeSeq = xml \\ "DartsConfig"
  def camRoot(id: String): NodeSeq = {
    if (file.lastModified() != lastModified) {
      try {
        xml = XML.loadFile("config.xml")
      }catch {
        case e: Exception => {
          Thread.sleep(20)
          xml = XML.loadFile("config.xml")
        }
      }
      x = xml \\ "DartsConfig"
      lastModified = file.lastModified
      println("config reloaded")
    }
    (x \\ "camera").filter(n => (n \ "@camId").text == id)
  }

  def int(path: String):Int = (path.split("/").foldLeft(x)((root, key) => root \\ key)).text.toInt
  def bool(path: String):Boolean = (path.split("/").foldLeft(x)((root, key) => root \\ key)).text.toInt == 1
  def str(path: String):String = (path.split("/").foldLeft(x)((root, key) => root \\ key)).text
  def float(path: String):Float = (path.split("/").foldLeft(x)((root, key) => root \\ key)).text.toFloat

  def int(id: String, path: String):Int = {
    val cr = camRoot(id)
    val x = (path.split("/").foldLeft(cr)((root, key) => root \\ key))
    x.text.toInt
  }
  def bool(id: String, path: String):Boolean = (path.split("/").foldLeft(camRoot(id))((root, key) => root \\ key)).text.toInt == 1
  def str(id: String, path: String):String = (path.split("/").foldLeft(camRoot(id))((root, key) => root \\ key)).text

  def getConfig(id: String):Config = new Config(id)
}
