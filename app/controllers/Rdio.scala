package controllers

import com.rdio.simple.RdioClient
import com.rdio.simple.RdioCoreClient

import helpers.Common.cfg
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.ServiceInfo
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.RequestHeader

object Rdio extends Controller {

  val KEY = ConsumerKey(cfg("rdio.key"), cfg("rdio.secret")) 
  val RDIO = OAuth(ServiceInfo(
    "http://api.rdio.com/oauth/request_token",
    "http://api.rdio.com/oauth/access_token",
    "https://www.rdio.com/oauth/authorize", KEY),
    false)

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

  def sessionTokenPair(request: RequestHeader): Option[RequestToken] = {
    for {
      token  <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  def getTracksInCollection = Action { request =>
    val token = sessionTokenPair(request).get.token
    val secret = sessionTokenPair(request).get.secret
    val rdio = new RdioCoreClient(new RdioClient.Consumer(KEY.key, KEY.secret),
      new RdioClient.Token(token, secret))

    val result = rdio.call("getTracksInCollection")
    Ok(result)
  }

}