package model

import com.datastax.driver.core.Row

import java.io.File
import java.util.Date

import play.api.mvc.MultipartFormData._
import play.api.libs.Files._

import scala.collection.JavaConversions._

import utils.Conversions

object ProjectFile {
	def undefined : ProjectFile = { 
		return ProjectFile(
			isDefined = false, 
			filename = "",
			originalName = "", 
			author = "", 
			projectId = -1
		)
	 }

 	def get(project : Project) : Seq[ProjectFile] = return CassieCommunicator.getFilesForProject(project)


	def saveFile (temporaryFile : (String, TemporaryFile), author : String, projectId : Int, timeSubmitted : Date) : ProjectFile = {
		//First move file

		//TODO: Generate UUID here, use that as the file name. Save original filename into database
		val uuid = java.util.UUID.randomUUID.toString;

		val originalName = temporaryFile._1
		val filename = uuid + "--" + temporaryFile._1

		val file = new File(s"uploads/$filename");
	    temporaryFile._2.moveTo(file, true);

	    return createProjectFile(filename, originalName, author, projectId, timeSubmitted);
	}

	def createProjectFile(filename : String, originalName : String, author : String, projectId : Int, timeSubmitted : Date) : ProjectFile = {
		val projectFile = ProjectFile(filename, originalName, author, projectId, timeSubmitted);
		return CassieCommunicator.addFile(projectFile);
	}

	implicit def fromRow(row : Row) : ProjectFile = {
		row match {
	  		case null => return ProjectFile.undefined
	  		case row : Row => {
	  			return ProjectFile(
	  				row.getString("filename"),
	  				row.getString("original_filename"),
	  				row.getString("author"),
	  				row.getInt("project_id"), 
	  				row.getDate("time_submitted")
				);
	  		}
  		}

	}
}

case class ProjectFile(filename : String, originalName : String,  author : String, projectId : Int, timeSubmitted : Date = new Date(), isDefined : Boolean = true)