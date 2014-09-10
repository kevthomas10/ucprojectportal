package controllers

import com.codahale.metrics.{Counter, Meter}
import com.kenshoo.play.metrics.MetricsRegistry
import com.typesafe.plugin._

import java.util.Date

import model._

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import utils._

object Application extends Controller with SessionHandler {

	val loginMeter = MetricsRegistry.default.meter("logins")
	val feedbackCounter = MetricsRegistry.default.counter("feedback");

	implicit val loginForm : Form[(String, String)] = Form(
		tuple(
			"username" -> nonEmptyText,
			"password" -> nonEmptyText
		).verifying("incorrect username or password", fields => fields match {
			case (username, password) => { User.authenticate(username, password).isDefined}
		}).verifying("user account not yet confirmed", fields => fields match {
			case (username, password) => { val user = User.get(username); user.hasConfirmed || !user.isDefined}
		})
	)

	val feedbackForm = Form(
		tuple(
			"author" -> nonEmptyText,
			"content" -> nonEmptyText,
			"type" -> nonEmptyText
		)
	)

	def checkAuthenticated (request : Request[play.api.mvc.AnyContent])(success : (String) => Result ) : Result = { 
		request.session.get("authenticated").map {
			authenticatedUser => success(authenticatedUser)
		}.getOrElse {
			Redirect(routes.Application.login(request.path));
		}
	}

	def javascriptRoutes = Action { implicit request =>
		import routes.javascript._
		Ok(
			Routes.javascriptRouter("jsRoutes")
			(
			 routes.javascript.ProjectController.submitUpdate,
			 routes.javascript.ProjectController.editProject,
			 routes.javascript.ProjectController.leaveProject,
			 routes.javascript.RequestController.join,
			 routes.javascript.RequestController.accept,
			 routes.javascript.RequestController.ignore,
			 routes.javascript.NotificationController.resetUnread,
			 routes.javascript.NotificationController.getUnreadCount,
			 routes.javascript.NotificationController.ignore,
			 routes.javascript.NotificationController.clearAll,
			 routes.javascript.AdminController.deleteUser,
			 routes.javascript.AdminController.deleteProject
			 )
		).as("text/javascript")
	}

	def index = Action { implicit request => {
		authenticated match {
			case Some(authenticatedUsername) => {
				val authenticatedUser = User.get(authenticatedUsername);
				Ok(views.html.index(authenticatedUser));
			}
		}


	}}

	def filter(filterStr : String) = Action { implicit request => {
		authenticated match {
			case Some(username) => {
				val authenticatedUser = User.get(username);
				Ok(views.html.filter(authenticatedUser, filterStr))
			}
		}
	}}

	def login(path : String) = Action {
		path match {
			case "/" => Redirect(routes.Application.login(""))
			case _ => Ok(views.html.login(path)(loginForm));
		}
		
	}

	def login : Action[play.api.mvc.AnyContent] = login("");


	def uploads(filename : String) = Action {
		val file : java.io.File = new java.io.File(s"uploads/$filename");
		if (file.exists()) {
		  	Ok.sendFile(
			    content = file,
			    fileName = _ => utils.Conversions.stripUUID(filename)
	  		)
		}
		else {
			BadRequest(s"$filename does not exist")
		}

	}

	def tryLogin(path : String) = Action { implicit request =>
		loginForm.bindFromRequest.fold(
			formWithErrors => {
				BadRequest(views.html.login()(formWithErrors))
			},
			loginData => {
				loginMeter.mark();
				if(path == "") {
					Redirect(routes.Application.index).withSession(
						"authenticated" -> loginData._1			)
				}
				else {
					Redirect(path).withSession(
						"authenticated" -> loginData._1			)
				}

			}
		)
	}



	def signout = Action { implicit request =>
		Redirect(routes.Application.login("")).withSession(
			request.session - "authenticated"
		)

	}

	def workshops = TODO

	def feedback = Action { implicit request =>
		authenticated match {
			case Some(username) => {
				val user = User.get(username);

				Ok(views.html.feedback(user)(feedbackForm))
			}
		}
	}

	def submitFeedback = Action { implicit request =>
		authenticated match {
			case Some(username) => {
				val user = User.get(username);
				feedbackForm.bindFromRequest.fold(
					formWithErrors => {
						BadRequest(views.html.feedback(user)(formWithErrors))
					},
					{ case (author : String, content : String, feedbackType : String) => {
						Feedback.create(author, content, feedbackType);

						feedbackCounter.inc();
						Ok(views.html.messages.prettyMessage(play.twirl.api.Html("your feedback has been sent!")))
					}}
				)
			}
		}
	}

}