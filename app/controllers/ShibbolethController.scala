package controllers

import com.codahale.metrics.Counter
import com.kenshoo.play.metrics.MetricsRegistry
import com.typesafe.plugin._

import java.util.Date

import model._
import model.UserPrivileges

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import utils._

object ShibbolethController extends Controller with SessionHandler {

	def secure = Action { implicit request => {
		//val formData = request.body.asFormUrlEncoded.get

		//println(formData)
		//println(request.headers.toString)

		request.headers.get("eppn") match {
			case Some(eppn) => {
				val username = utils.Conversions.eppnToUsername(eppn);
				println(username);

				User.get(username) match {
					case x : User if x.isDefined == false => {
						//Create the user in the Project Portal's database
						println(x);
						println("Creating user");
						User.createFromShibboleth(username);
						Redirect(routes.ActivationController.activateNEW("")).withSession("authenticated" -> username)
					}
					case user : User => Redirect(routes.Application.index).withSession("authenticated" -> username)
				}
			}
			case None => NotFound(views.html.messages.notFound("There was some kind of issue retrieving your information from the Central Login System. "))
		}


	}}
}