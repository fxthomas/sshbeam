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
import com.jcraft.jsch.JSchException
import java.net.ConnectException
import java.net.SocketException

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

    // Start the notification
    runOnUiThread(
      startForeground(1,
        builder
        .setTicker("Starting transfer")
        .setContentTitle(filename)
        .setContentText("Starting transfer")
        .setOngoing(true).build
      )
    )

    // Create the session and the monitor
    val session = server.createSession(auth)
    implicit val monitor = Monitor(filename, size)

    // Send the file
    try {
      session.connect
      session.cd(destination)
      session.put(filename, is)
    } catch { case e: Throwable => failWith(e)
    } finally { session.disconnect; is.close }

    // Stop the foreground service
    stopForeground(false)
  }

  def failWith(e: Throwable) = {
    val message = e match {
      case ex: JSchException => Option(ex.getCause) match {
        case Some(_: ConnectException) => "Connection failed"
        case Some(_: SocketException) => "Transfer interrupted"
        case Some(exc: Throwable) => exc.getMessage
        case _ => ex.getMessage
      }
      case _ => e.getMessage
    }

    runOnUiThread {
      notificationManager.notify(0,
        builder.setProgress(0, 0, false)
        .setTicker("Transfer failed")
        .setOngoing(false)
        .setContentText(message)
        .build
      )
    }
    e.printStackTrace
  }

  case class Monitor(filename: String, size: Long) extends SftpProgressMonitor {

    def isComplete = (progress == size)
    var isRunning = false
    var startTime = 0L
    var progress = 0L

    def end = {
      val message = if (isComplete) "Transfer complete!"
                    else "Transfer failed"

      runOnUiThread {
        notificationManager.notify(0,
          builder.setProgress(0, 0, false)
                 .setContentText(message)
                 .setTicker(message)
                 .setOngoing(false)
                 .build
        )
      }
    }

    def count(cnt: Long): Boolean = {
      // Increment progress
      progress += cnt

      // Estimate the time left
      val telapsed = (System.currentTimeMillis - startTime).toDouble / 1000.
      val speed = progress.toDouble / telapsed
      val tleft = (size / speed).toInt

      // Create a "time left" string
      val s_speed = (
        if (speed < 1000.) s"${speed.toLong} B/s"
        else if (speed < 1000000.) { (speed / 1000.).toInt.toString + "kB/s" }
        else if (speed < 1000000000.) { (speed / 1000000.).toInt.toString + "MB/s" }
        else { (speed / 1000000000.).toInt.toString + "GB/s" }
      )

      // Create a "time left" string
      val s_left = (
        if (tleft < 60) s"$tleft seconds left ($s_speed)"
        else if (tleft < 60 * 60) { (tleft / 60).toString + s" minutes left ($s_speed)" }
        else if (tleft < 60 * 60 * 24) { (tleft / 60 / 60).toString + s" hours left ($s_speed)" }
        else { (tleft / 60 / 60 / 24).toString + s" days left ($s_speed)" }
      )

      // Update the spinner
      runOnUiThread {
        startForeground(1,
          builder
          .setProgress(size.toInt, progress.toInt, false)
          .setContentText(s_left)
          .build
        )
      }

      // Return running state
      return isRunning
    }

    def init(op: Int, src: String, dest: String, max: Long) {
      // Update running state and progress
      isRunning = true
      progress = 0L
      startTime = System.currentTimeMillis

      // Update the spinner
      runOnUiThread {
        startForeground(1,
          builder.setProgress(size.toInt, progress.toInt, false)
                 .setOngoing(true)
                 .setContentText("Connected")
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
