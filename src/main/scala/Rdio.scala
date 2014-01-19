package controllers

import daos.RdioDao
import helpers.Common.cfg
import helpers.Common.sessionTokenPair
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.ServiceInfo
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.RequestHeader

object Rdio extends Controller {

  val KEY = ConsumerKey(cfg("rdio.consumer_key"), cfg("rdio.consumer_secret")) 
  val RDIO = OAuth(ServiceInfo(
    "http://api.rdio.com/oauth/request_token",
    "http://api.rdio.com/oauth/access_token",
    "https://www.rdio.com/oauth/authorize", KEY),
    false)

  /**
   * OAuth protocol (basic idea):
   * 1) Retrieve request token from Rdio.
   * 2) Redirect user to Rdio authorization page to authorize app.
   *    If user authorizes oauth_verifier is returned.
   * 3) With oauth_verifier request token gets upgraded to access token.
   * 
   * For more details: http://www.rdio.com/developers/docs/web-service/oauth/
   */
  def authenticate = Action { request =>
    request.queryString.get("oauth_verifier").flatMap(_.headOption).map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      RDIO.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          Redirect(routes.Application.index).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
      RDIO.retrieveRequestToken("http://localhost:9000/rdio/auth") match {
        case Right(t) => {
          Redirect(RDIO.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      })
  }

  def currentUser = Action { request =>
    val result = getRawJsonData("currentUser", request)
    Ok(result)
  }

  def getTracksInCollection = Action { request =>
    val result = getRawJsonData("getTracksInCollection", request)
    Ok(result)
  }
  
  /**
   * Gets JSON response for then given Rdio method. For a full list of methods
   * please see http://www.rdio.com/developers/docs/web-service/methods/
   */
  def getRawJsonData(method: String, request: RequestHeader) = {
    val tokenPair = sessionTokenPair(request)
    tokenPair match {
      case None => "Sorry Oauth token pair was not found"
      case Some(tp) => {
        val token = tp.token
        val secret = tp.secret
        new RdioDao(token, secret).call(method)
      }
    }
  }

}