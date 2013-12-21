package controllers

import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.RequestHeader

object Application extends Controller {

  def index = Action {
    Ok("Hello")
  }

}
