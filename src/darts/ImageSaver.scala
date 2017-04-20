package darts

import java.awt.image.BufferedImage
import java.io.File
import javax.swing.ImageIcon

import org.bytedeco.javacpp.PGRFlyCapture
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgcodecs.imwrite
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.swing.{SimpleSwingApplication, _}
import scala.swing.event.{ButtonClicked, WindowClosing}

/**
 * Created by vassdoki on 2016.08.11.
  *
  * This is for capturing and saving sample images without any processing.
  *
 */
object ImageSaver extends  SimpleSwingApplication{

  var guiCreated = false

  val cameraCheckbox = new CheckBox("Use Camera")
  val imageViews: List[Label] = List.fill(4) {
    new Label
  }
  val fpsLabel = new Label
  var imgCount = 0
  var openedImage: Mat = null
  var openedImageClone: Mat = null

  val defaultDirectory = "/home/vassdoki/darts/v2/cam-aug11"
  private lazy val fileChooser = new FileChooser(new File(defaultDirectory))


  def top: Frame = new MainFrame {
    guiCreated = true
    val buttonsPanel = new FlowPanel() {
      contents += cameraCheckbox
//      contents += new Button(openImageAction)
      contents += fpsLabel
//      for (i <- 0 to 3) { contents += transCheckbox(i) }
      vGap = 1
    }

    contents = new BorderPanel() {
      add(new FlowPanel(buttonsPanel), BorderPanel.Position.North)
      add(
        new GridPanel(rows0 = Math.sqrt(imageViews.size).toInt, cols0 = Math.sqrt(imageViews.size).toInt) {
          for (i <- 0 to imageViews.size-1) {contents += new ScrollPane(imageViews(i))}
          preferredSize = new Dimension(1024, 768)
        }, BorderPanel.Position.Center)
    }

    listenTo(cameraCheckbox)

    reactions += {
      case ButtonClicked(c) => {
        if (c == cameraCheckbox) {
          setCameraState
        }
      }
      case e: WindowClosing => {
        println("WIndowClosing event")
      }
      case e => {
        //println("Unhandeled event: " + e)
      }
      //      case e: window
    }

    override def closeOperation(): Unit = {
      Thread.sleep(50)
      println("Closing applicatoin")
      top.close()
      exit(0)
    }
  }

  var runRecorder = false
  def setCameraState = {
    if (cameraCheckbox.selected) {
      runRecorder = true
      val fut1 = Future {
        val capture1 = new CaptureDevice("2")
        Thread.sleep(1000)
        val capture2 = new CaptureDevice("3")
        Thread.sleep(1000)
        val capture3 = new CaptureDevice("1")
        var i1 = new Mat
        var i2 = new Mat
        var i3 = new Mat
        var toBufferedImage: BufferedImage = null
        
        while(runRecorder) {
          i1 = capture1.captureFrame(i1)
          i2 = capture2.captureFrame(i2)
          i3 = capture3.captureFrame(i3)

          if (Config.bool("SAVE_CAPTURED")) {
            var time = s"${Config.timeFormatter.print(DateTime.now)}"
            imwrite(f"${Config.str("OUTPUT_DIR")}/d1-$time.jpg", i1)
            imwrite(f"${Config.str("OUTPUT_DIR")}/d2-$time.jpg", i2)
            imwrite(f"${Config.str("OUTPUT_DIR")}/d3-$time.jpg", i3)
          }
        }
        capture1.release
        capture2.release
        capture3.release
        i1.release()
        i2.release()
        i3.release()
      }
    } else {
      runRecorder = false
    }
  }

  def updateImage(imgNum: Int, imageIcon: ImageIcon) = synchronized {
    if (guiCreated) {
      Swing.onEDT {
        imageViews(Math.abs(imgNum)).icon = imageIcon
        fpsLabel.text = f"C: $imgCount"
      }
    }
  }

}
