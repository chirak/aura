package controllers

import com.rdio.simple.RdioClient
import com.rdio.simple.RdioCoreClient

import helpers.Common.sessionTokenPair
import helpers.Common.isLoggedIn
import play.api.Play
import play.api.Play.current
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.ServiceInfo
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.RequestHeader

object Application extends Controller {
  
  def index = Action { request =>
    // At the moment I don't feel like implementing a User model. So just check
    // to see if we have a Rdio access token to be considered "logged in"
    val session = sessionTokenPair(request)
    Ok(views.html.index("Hello, welcome to Aura", isLoggedIn(session)))
  }

}
