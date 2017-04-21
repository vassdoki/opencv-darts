package experimental

import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_videoio.VideoCapture

/**
  * Created by vassdoki on 2017.04.21..
  */
class CapTest(val num: Int) {
  var capture: VideoCapture = null
  if (num < 2) {
     capture = new VideoCapture(num)
  }
  def read(m: Mat): Mat = {
    capture.read(m)
    m
  }

}
