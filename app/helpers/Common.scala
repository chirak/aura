package helpers

import play.api.Play
import play.api.Play.current
import play.api.libs.oauth.RequestToken
import play.api.mvc.RequestHeader

object Common {

  def cfg(key: String) = {
    val config = Play.application.configuration
    config.getString(key).getOrElse(throw new NoSuchElementException(key))
  }

  def sessionTokenPair(request: RequestHeader): Option[RequestToken] = {
    for {
      token  <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }
  
  def isLoggedIn(requestToken: Option[RequestToken]) = {
    requestToken match {
      case None => false
      case Some(_) => true
    }
  }

}