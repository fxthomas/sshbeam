package io.github.fxthomas.sshbeam

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.File

object SharedObject {

  /**
   * Prepares a shared object from an intent
   */
  def apply(intent: Intent)(implicit ctx: Context): Option[SharedObject] = {
    // Retrieve the URI of the intent
    val uri =
      Option(intent.getParcelableExtra(Intent.EXTRA_STREAM).asInstanceOf[Uri])

    // Return a shared object
    uri match {
      case Some(u) => Some(new UriSharedObject(u))
      case None => createTextShare(intent)
    }
  }

  def createTextShare (intent: Intent): Option[SharedObject] = {
    // If we can't find the URI, then somebody shared text directly.
    // In which case, we use that text to create a new file.
    val text = intent.getStringExtra(Intent.EXTRA_TEXT)

    // If the user shared _nothing_, send a toast and finish
    if (text == null) None
    else {

      // Create the title from either the intent or the text
      val title = Option(intent.getStringExtra(Intent.EXTRA_SUBJECT))
        .getOrElse {
          val trimmed = text.trim
          val length = trimmed.length
          trimmed.substring(0, if (length < 10) length else 10)
        }.replaceAll("[\\W]+|_", "-")

      // Add an extension
      val ext = intent.getType match {
        case "text/html" => "html"
        case "text/xml" => "xml"
        case "application/xml" => "xml"
        case "image/png" => "png"
        case "image/jpg" => "jpg"
        case _ => "txt"
      }

      // Return a shared object
      Some(new TextSharedObject(s"${title}.${ext}", text))
    }
  }
}

trait SharedObject {
  val size: Long
  var name: String
  def inputStream: InputStream
}

class UriSharedObject(uri: Uri)(implicit ctx: Context)
extends SharedObject {

  var name = (
    uri.getScheme match {

      case "content" => {
        // Query the content resolver for the content:// URI's filename
        val query = Option(ctx.getContentResolver.query(
          uri,
          Array(MediaStore.MediaColumns.DATA),
          null, null, null)
        )

        // Retrieve the filename column
        query flatMap { cr =>
          try {
            if (cr.moveToFirst)
              Option(cr.getString(0)).map(new File(_).getName)
            else None
          } finally {
            cr.close
            None
          }
        }
      }

      case "file" => Option(uri.getLastPathSegment)
      case _ => None
    }
  ).getOrElse("untitled.txt")

  val size = {
    val fd = ctx.getContentResolver.openFileDescriptor(uri, "r")
    val size = fd.getStatSize
    fd.close; size
  }

  def inputStream = ctx.getContentResolver.openInputStream(uri)
}

class TextSharedObject(fname: String, contents: String)
extends SharedObject {
  var name = fname
  val size = contents.getBytes("UTF-8").length.toLong
  def inputStream = new ByteArrayInputStream(contents.getBytes("UTF-8"))
}
