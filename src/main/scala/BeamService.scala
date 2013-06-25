package io.github.fxthomas.sshbeam

import android.app._
import android.os._
import android.content._
import android.net._
import android.provider._
import android.preference._
import android.view._

import org.scaloid.common._

import com.jcraft.jsch.SftpProgressMonitor

import Sftp._
import Helpers._

import java.io.ByteArrayInputStream

case class MissingParameterException(message: String) extends Exception(message)
class UnknownTypeException extends Exception("Unknown type")

class BeamService extends IntentService("SSH Beam") {

  import BeamService._

  implicit val ctx = this

  // Configure the notification
  lazy val builder = new Notification.Builder(ctx)
    .setContentTitle("Transfer in progress")
    .setContentText("Preparing transfer")
    .setSmallIcon(R.drawable.icon_ticker)
    .setProgress(1, 0, false)

  // Notification manager
  def notificationManager = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

  override def onHandleIntent(intent: Intent) = {

    // Start the notification
    runOnUiThread {
      notificationManager.notify(0,
        builder.setTicker("Starting transfer").setOngoing(true).build)
    }

    // Create the input stream
    val (is, size) = Option(intent.getData) match {
      case Some(uri) => (uri.inputStream, uri.dataSize)
      case None => {
        val data = intent.getStringExtra(Intent.EXTRA_TEXT).getBytes("UTF-8")
        val size = data.length.toLong
        (new ByteArrayInputStream(data), size)
      }
    }

    // Retrieve transfer informations
    val filename = intent.getStringExtra(EXTRA_NAME)
    val destination = intent.getStringExtra(EXTRA_DESTINATION)
    val server = intent.getParcelableExtra(EXTRA_SERVER).asInstanceOf[SftpServer]
    val auth = intent.getParcelableExtra(EXTRA_AUTH).asInstanceOf[SftpAuth]

    // Create the session and the monitor
    val session = server.createSession(auth)
    implicit val monitor = Monitor(size)

    // Send the file
    try {
      session.connect
      session.cd(destination)
      session.put(filename, is)
    } catch {
      case e: Throwable => runOnUiThread {
        notificationManager.notify(0,
          builder.setProgress(0, 0, false)
                 .setTicker("Transfer failed")
                 .setContentTitle("Transfer failed")
                 .setContentText(e.getMessage)
                 .setOngoing(false)
                 .build
        )
        e.printStackTrace
      }
    } finally {
      session.disconnect
      is.close
    }
  }

  case class Monitor(size: Long) extends SftpProgressMonitor {

    def isComplete = (progress == size)
    var isRunning = false
    var progress = 0L

    def end = {
      val message = if (isComplete) "Transfer complete!"
                    else "Transfer failed"

      runOnUiThread {
        notificationManager.notify(0,
          builder.setProgress(0, 0, false)
                 .setContentTitle(message)
                 .setTicker(message)
                 .setOngoing(false)
                 .build
        )
      }
    }

    def count(cnt: Long): Boolean = {
      // Increment progress
      progress += cnt

      // Update the spinner
      runOnUiThread {
        notificationManager.notify(0,
          builder.setProgress(size.toInt, progress.toInt, false).build
        )
      }

      // Return running state
      return isRunning
    }

    def init(op: Int, src: String, dest: String, max: Long) {
      // Update running state and progress
      isRunning = true
      progress = 0L

      // Update the spinner
      runOnUiThread {
        notificationManager.notify(0,
          builder.setProgress(size.toInt, progress.toInt, false)
                 .setContentText(dest)
                 .setOngoing(true)
                 .build
        )
      }
    }
  }
}

object BeamService {
  val EXTRA_NAME = "io.github.fxthomas.sshbeam.Beam.FILENAME"
  val EXTRA_DESTINATION = "io.github.fxthomas.sshbeam.Beam.DESTINATION"
  val EXTRA_SERVER = "io.github.fxthomas.sshbeam.Beam.SERVER"
  val EXTRA_AUTH = "io.github.fxthomas.sshbeam.Beam.AUTH"
}
