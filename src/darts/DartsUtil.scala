package darts

import org.bytedeco.javacpp.opencv_core.Point

/**
 * Created by vassdoki on 2016.11.28..
 */
object DartsUtil {
  def identifyNumber(p: Point): Pair[Int, Int] = {
    val degree = CvUtil.getDegreeFromBull(p)
    val distance = CvUtil.getDistanceFromBull(p)
    // 0 degree is the middle of sector 6

    val int: Int = Math.floor((degree + 9) / 18).toInt
    val number = if (int > 19) Config.nums(0) else Config.nums(int)

    val circleNumber: Int = Config.distancesFromBull filter { dfb => dfb < distance } length

    circleNumber match {
      case 0 => (2, 25)
      case 1 => (1, 25)
      case 2 => (1, number.toInt)
      case 3 => (3, number.toInt)
      case 4 => (1, number.toInt)
      case 5 => (2, number.toInt)
      case 6 => (0, number.toInt)
    }
  }

}
