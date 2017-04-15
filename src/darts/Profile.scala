package darts

import scala.collection.mutable

/**
  * Created by vassdoki on 2017.04.07..
  */
object Profile {
  val data = mutable.HashMap.empty[String, Measurment]
  val started = mutable.HashMap.empty[String, Long]

  case class Measurment(count: Int, time: Long)

  def start(key: String): Unit = {
    start(key, "")
  }
  def start(key: String, key2: String): Unit = {
    if (Config.bool("DEBUG_PROFILE")) println(s"Profile.start: key: $key key2: $key2")
    val skey = s"$key#$key2"
    started(skey) = System.currentTimeMillis
  }
  def end(key: String):Unit = {
    end(key, "")
  }
  def end(key: String, key2: String):Unit = {
    if (Config.bool("DEBUG_PROFILE")) println(s"Profile.end: key: $key key2: $key2")
    val skey = s"$key#$key2"
    val time = System.currentTimeMillis() - started(skey)
    if (data.isDefinedAt(key)) {
      data(key) = Measurment(data(key).count+1, data(key).time + time)
    } else {
      data(key) = Measurment(1, time)
    }
    started.remove(skey)
  }
  def print = {
    for((k,v) <- data) {
      println(f"${v.count}%5d;${v.time.toDouble/1000d}%6.4f;${v.time.toDouble/v.count/1000d}%8.6f;$k")
    }
  }
}

