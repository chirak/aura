package helpers

import play.api.Play
import play.api.Play.current

object Common {

  def cfg(key: String) = {
    val config = Play.application.configuration
    config.getString(key).getOrElse(throw new NoSuchElementException(key))
  }

}