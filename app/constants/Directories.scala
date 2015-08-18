package constants

import play.Play

object Directories {
	val Uploads = Play.application.configuration.getString("directory.uploads");
	val INDEXES = Play.application.configuration.getString("directory.indexes");
}