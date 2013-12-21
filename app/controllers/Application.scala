package controllers

import com.rdio.simple.RdioClient
import com.rdio.simple.RdioCoreClient

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
    val isLoggedIn = false
    Ok(views.html.index("Hello, welcome to Aura", isLoggedIn))
  }

}
