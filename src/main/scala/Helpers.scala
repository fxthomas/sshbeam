package io.github.fxthomas.sshbeam

import android.os.Parcelable
import android.os.Parcel
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.File

object Helpers {

  implicit class UriEx(val uri: Uri) extends AnyVal {

    /**
     * Returns the name of the content pointed to by the Uri
     */
    def dataName(implicit ctx: Context): Option[String] = uri.getScheme match {
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

    /**
     * Return the size of the content pointed to by the Uri
     */
    def dataSize(implicit ctx: Context) = {
      val fd = ctx.getContentResolver.openFileDescriptor(uri, "r")
      val size = fd.getStatSize
      fd.close; size
    }

    /**
     * Opens an input stream
     */
    def inputStream(implicit ctx: Context) =
      ctx.getContentResolver.openInputStream(uri)
  }

  def createFilename(
    mimeType: Option[String],
    text: Option[String],
    subject: Option[String]): String = {

    // Add an extension
    val ext = mimeType match {
      case Some("text/html") => "html"
      case Some("text/xml") => "xml"
      case Some("application/xml") => "xml"
      case Some("image/png") => "png"
      case Some("image/jpg") => "jpg"
      case _ => "txt"
    }

    lazy val alttitle = text map { t =>
      val trimmed = t.trim
      val length = trimmed.length
      trimmed.substring(0, if (length < 10) length else 10)
    }

    // Create the title from either the intent or the text
    val title = subject
      .orElse(alttitle)
      .getOrElse("untitled")
      .replaceAll("[\\W]+|_", "_")

    // Return a shared object
    s"${title}.${ext}"
  }
}
