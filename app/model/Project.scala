package model

import com.datastax.driver.core.Row

import com.github.nscala_time._
import com.github.nscala_time.time._
import com.github.nscala_time.time.Imports._

import java.util.Date

import org.joda.time.{Weeks, Years}

import play.api.libs.json._
import play.api.mvc._

import scala.collection.JavaConversions._
import scala.util.Random

import utils._
import utils.nosql.CassieCommunicator

object Project {

	def apply (name : String) : Project = new Project(name);
	def apply (name : String, description : String, categories : Seq[String], teamMembers : Seq[String]) : Project = { 
		new Project(name, description, categories = categories, teamMembers = teamMembers);
	}

	def unapplyIncomplete(project : Project) : Option[(String, String, List[String], List[String])] = Some(project.name, project.description, project.categories.toList, project.teamMembers.toList);

	def undefined : Project = {
		return Project(
			isDefined = false,
			id = -1,
			name = "",
			description = "",
			categories = List[String]("null")
		)
	}

	def create (name: String, description : String, primaryContact : String, categories : Seq[String], teamMembers : Seq[String]) : Project = {

		val project = Project(name, description, categories, primaryContact :: teamMembers.toList);

		val newProject = CassieCommunicator.addProject(project, primaryContact, categories, primaryContact :: teamMembers.toList);

		for (username <- teamMembers) {
			if(username != primaryContact) {
				Project.addUser(newProject.id, User.get(username))
			}
		}

		return newProject
	}

	def all : Seq[Project] = CassieCommunicator.getProjects;
	def allSorted : Seq[Project] = {
		return Project.all.sortWith( (x,y) => {
			val xInProgress = (x.state == ProjectState.IN_PROGRESS || x.state == ProjectState.IN_PROGRESS_NEEDS_HELP);
			val yInProgress = (y.state == ProjectState.IN_PROGRESS || y.state == ProjectState.IN_PROGRESS_NEEDS_HELP);

			if(xInProgress && yInProgress) {
				Random.nextBoolean()
			}
			else if(xInProgress && !yInProgress) {
				 true;
			}
			else if(yInProgress && !xInProgress) {
				 false;
			}
			else {
				val now = DateTime.now;

				val sinceX = Years.yearsIn(new DateTime(x.timeStarted) to now).getYears;
				val sinceY = Years.yearsIn(new DateTime(y.timeStarted) to now).getYears;

				if(sinceX < 1 && sinceY < 1 || sinceX >= 1 && sinceY >= 1) {
					 Random.nextBoolean()
				}
				else if(sinceX < 1 && sinceY >= 1) {
					 true;
				}
				else {
					 false;
				}
			}
		} )
	}

	def get(username : String) : Seq[Project] = {
		return CassieCommunicator.getProjectsForUsername(username);
	}

	def get(id : Int) : Project = {
		return CassieCommunicator.getProject(id);
	}

	def getPrimaryForUsername(username : String) : Seq [Project] = {
		return CassieCommunicator.getPrimaryProjectsForUsername(username);
	}

	def update(project : Project) {
		if(!project.isDefined) {
			return
		}
		CassieCommunicator.updateProject(project);
	}


	def delete(id : Int) {
		Project.getUpdates(id).foreach(update => update.delete())
		val project = Project.get(id);

		project.teamMembers.foreach(username => project.removeUser(User.get(username)));
		
		CassieCommunicator.removeProject(Project.get(id));
	}

	def close(project : Project) {
		if(project.isNew && Project.getUpdates(project.id).length == 0) {
			delete(project.id);
		}
		else {
			Project.update(Project(
					id = project.id,
					name = project.name,
					description = project.description,
					categories = project.categories,
					state = ProjectState.CLOSED,
					stateMessage = project.stateMessage,
					teamMembers = project.teamMembers,
					primaryContact = null,
					timeFinished = project.timeFinished
			))
		}
	}

	def allTags : Seq[String] = return CassieCommunicator.getTagsWithType("project").getOrElse { return List[String]() };


	def addUser(id : Int, user : User) : Project = {
		val project = Project.get(id);

		if(!project.isDefined) {
			return Project.undefined
		}

		CassieCommunicator.addUserToProject(user, project);
		Notification.createAddedToProject(user, project);
		return Project.get(id);
	}

	def removeUser(id : Int, user : User) : Project = {
		val project = get(id);

		if(!project.isDefined) {
			return Project.undefined
		}
		
		CassieCommunicator.removeUserFromProject(user, project);

		if(project.primaryContact == user.username) {
			Project.removePrimaryContact(id)
		}

		return Project.get(id);
	}

	def removePrimaryContact(id : Int) : Project = {
		val project = Project.get(id);

		if(!project.isDefined) {
			return Project.undefined
		}

		println(s"Num of members = ${project.teamMembers.length}")

		if(project.teamMembers.length <= 1) {
			Project.changePrimaryContact(id, User.get(project.primaryContact), User.undefined);
			Project.close(project);
			return Project.get(id);
		}
		else {
			var otherUser : User = User.undefined;
			project.teamMembers.toStream.takeWhile(_ => otherUser == null).foreach({ member =>
				if(member != project.primaryContact) {
					otherUser = User.get(member);
				}
			})
			return Project.changePrimaryContact(id, User.get(project.primaryContact), otherUser)
		}
	}

	def changePrimaryContact(id : Int, oldUser : User, newUser : User) : Project = {
		val oldProject = Project.get(id);

		if(!oldProject.isDefined) {
			return Project.undefined
		}

		CassieCommunicator.changePrimaryContactForProject(oldUser, newUser, oldProject);
		ProjectRequest.swapOwner(id, oldUser.username, newUser.username)

		return Project.get(id);
	}

	def getUpdates(id : Int) : Seq[ProjectUpdate] = CassieCommunicator.getUpdatesForProject(id);

	implicit def fromRow (row : Row) : Project =  { 
		row match {
			case null => Project.undefined
			case row : Row => {
				return Project(
					name= row.getString("name"),
		 			id = row.getInt("id"), 
		 			description= row.getString("description"), 
		 			primaryContact = row.getString("primary_contact"),
		 			categories = row.getSet("categories", classOf[String]).toList,
		 			teamMembers = row.getSet("team_members", classOf[String]).toList,
		 			state = row.getString("state"),
		 			stateMessage = row.getString("state_message"),
		 			timeStarted = row.getDate("time_started"),
		 			timeFinished = row.getDate("time_finished")
				);
			}
		}
	 }
}

case class Project (id : Int, name: String, description : String,
				 	timeStarted : Date = new Date(), timeFinished : Date = null,
					categories : Seq[String] = List[String](), tags : Seq[String] = List[String](),
 					primaryContact : String = "", teamMembers : Seq[String] = List[String](), 
 					state : String = "", stateMessage : String = "",
 					isDefined : Boolean = true) {
	def this (name : String, description : String, categories : Seq[String], teamMembers : Seq[String]) = this(-1, name, description, categories=categories, teamMembers=teamMembers)
	def this (name : String) = this(name, description = "", categories = List[String](), teamMembers = List[String]());

	def notifyMembersExcluding(excludingUsername : String, updateContent : String) {
		teamMembers.foreach(username => if(excludingUsername.equals(username) == false) Notification.createUpdate(User.get(username), User.get(excludingUsername), this, updateContent));
	}

	def isNew : Boolean = Weeks.weeksBetween(new DateTime(timeStarted), DateTime.now).getWeeks <= 1;

	def removeUser(user : User) : Project = Project.removeUser(id, user);
}