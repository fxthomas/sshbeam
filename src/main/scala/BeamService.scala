package io.github.fxthomas.sshbeam

import android.app._
import android.os._
import android.content._
import android.net._
import android.provider._
import android.preference._
import android.view._

import com.jcraft.jsch._
import java.util.Properties
import java.io.{File, InputStream, FileOutputStream, PrintStream}

import org.scaloid.common._

import scala.io.Source
import scala.concurrent._
import scala.collection.JavaConversions._

case class MissingParameterException(message: String) extends Exception(message)
class UnknownTypeException extends Exception("Unknown type")

class BeamService extends IntentService("SSH Beam") {
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

    runOnUiThread {
      notificationManager.notify(0, builder.setTicker("Starting transfer").build)
    }

    val share = intent.getStringExtra("s_type") match {
      case "uri" => UriSharedObject(
        intent.getParcelableExtra("s_uri").asInstanceOf[Uri]
      )

      case "text" => TextSharedObject(
        intent.getStringExtra("s_fname"),
        intent.getStringExtra("s_contents")
      )
      case _ => throw new UnknownTypeException
    }

    val filename = intent.getStringExtra("filename")
    val server = intent.getStringExtra("server")
    val username = intent.getStringExtra("username")
    val port = intent.getIntExtra("port", 22)
    val destination = intent.getStringExtra("destination")
    val auth = intent.getStringExtra("auth")
    val password = intent.getStringExtra("password")

    val transfer = Transfer(
      share,
      filename,
      destination,
      server,
      port,
      username,
      auth)

    transfer.start(password)
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

  def generateKeyPair(server: String, username: String) = {
    // Preflight checks
    if (server == null || username == null ||
        server.isEmpty || username.isEmpty)
      throw MissingParameterException("Please configure your server address and username!")

    // Generate a canonical name for the key pair
    val fserver = "[^\\w]+".r.replaceAllIn(server, "_")
    val fusername = "[^\\w]+".r.replaceAllIn(username, "_")
    val filename = s"$fserver-$fusername"

    // If the key isn't generated, generate it, write it and return it
    if (!(ctx.fileList contains filename)) {

      // Generate the key
      val key = KeyPair.genKeyPair(new JSch, KeyPair.DSA)

      // Write private key
      val fpriv = ctx.openFileOutput(filename, Context.MODE_PRIVATE)
      key.writePrivateKey(fpriv)
      fpriv.close

      // Write public key
      val fpub = ctx.openFileOutput(filename + ".pub", Context.MODE_PRIVATE)
      key.writePublicKey(fpub, "sshbeam@android")
      fpub.close
    }

    // Return the name of the private key file
    new File(ctx.getFilesDir, filename)
  }

  case class Transfer(
    share: SharedObject,
    filename: String,
    destination: String,
    server: String,
    port: Int,
    username: String,
    auth: String) {

    // Create the input stream
    val is = share.inputStream

    def sendPublicKey(implicit monitor: SftpProgressMonitor) = {
      // Create the session
      val sftp = new SftpServer(server, port, username)
      val key = generateKeyPair(server, username)
      val session = sftp.createPublicKeySession(key)
      send(session)
    }

    def sendPassword(password: String)(implicit monitor: SftpProgressMonitor) = {
      // Create the session
      val sftp = new SftpServer(server, port, username)
      val session = sftp.createPasswordSession(password)
      send(session)
    }

    def send(session: SftpSession)(implicit monitor: SftpProgressMonitor) = {
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
        }
      } finally {
        session.disconnect
        is.close
      }
    }

    def start(password: String): Unit = {

      // Create a monitor
      implicit val m = Monitor(share.size)

      // Send the file
      auth match {
        case "password" => sendPassword(password)
        case "public_key" => sendPublicKey
        case _ => throw new Exception("Oops, wrong auth method!")
      }
    }
  }
}
