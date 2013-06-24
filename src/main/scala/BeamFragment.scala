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

import ExecutionContext.Implicits.global

case class MissingParameterException(message: String) extends Exception(message)

class BeamTransferFragment extends Fragment {

  def savePassword(p: String) = {
    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    val edit = prefs.edit
    edit.putString("ssh_auth_password", p)
    edit.commit
  }

  def clearPassword = savePassword("")

  implicit def ctx = getActivity
  implicit val tag = LoggerTag("BeamTransfer")

  override def onCreate(bundle: Bundle) = {
    super.onCreate(bundle)
    setRetainInstance(true)
  }

  override def onPause = {
    super.onPause
    monitor.foreach(_.pause)
  }

  override def onResume = {
    super.onResume
    monitor.foreach(_.createSpinner)
  }

  override def onDestroy = {
    super.onDestroy
    monitor.foreach(_.end)
  }

  var spinner: Option[ProgressDialog] = None
  var monitor: Option[Monitor] = None

  case class Monitor(size: Long) extends SftpProgressMonitor {

    var isRunning = false
    var progress = 0L

    def isComplete = (progress == size)

    monitor = Some(this)

    def createSpinner = if (!spinner.isDefined) {
      val spin = new ProgressDialog(ctx)
      spin setProgressStyle ProgressDialog.STYLE_HORIZONTAL
      spin setTitle "SSH Beam"
      spin setMessage "Preparing transfer..."
      spin setIndeterminate false
      spin setMax size.toInt
      spin setProgress progress.toInt
      spin setCancelable false
      spin.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int) = end
      })
      spin.show
      spinner = Some(spin)
    }

    def pause = {
      spinner.foreach(s => runOnUiThread(s.dismiss))
      spinner = None
    }

    def end = {
      pause
      monitor = None
      isRunning = false
    }

    def count(cnt: Long): Boolean = {
      // Increment progress
      progress += cnt

      // Update the spinner
      for (spin <- spinner) runOnUiThread {
        spin setProgress progress.toInt
      }

      // Return running state
      return isRunning
    }

    def init(op: Int, src: String, dest: String, max: Long) {
      // Update running state and progress
      isRunning = true
      progress = 0L

      // Create spinner if needed
      if (!spinner.isDefined) createSpinner

      // Prepare spinner
      for (spin <- spinner) runOnUiThread {
        spin setMessage "Transfer in progress..."
        spin setProgress 0
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

  def generatePublicKey(server: String, username: String)(pk: String => Unit) = {
    // Load the public key
    val pubkey: Future[String] = future {
      val kf = generateKeyPair(server, username)
      val ki = ctx.openFileInput(kf.getName + ".pub")
      val pubkey = Source.fromInputStream(ki).mkString
      ki.close
      pubkey
    }

    // Show the spinner dialog
    val dlg = new ProgressDialog(ctx)
    dlg setTitle "SSH Beam"
    dlg setMessage "Generating key pair..."
    dlg show

    // Share it
    pubkey onSuccess {
      case key: String => runOnUiThread {
        pk(key)
        dlg.dismiss
      }
    }

    // If the generation failed, display a toast
    pubkey onFailure {
      case exc: MissingParameterException =>
        runOnUiThread(dlg.dismiss)
        toast(exc.getMessage)
    }
  }

  def transfer(
    share: SharedObject,
    destination: String,
    server: String,
    port: Int,
    username: String,
    auth: String,
    shouldSavePassword: Boolean,
    password: Option[String]) =
    Transfer(share, destination, server, port,
             username, auth, shouldSavePassword).start(password)


  case class Transfer(
    share: SharedObject,
    destination: String,
    server: String,
    port: Int,
    username: String,
    auth: String,
    shouldSavePassword: Boolean
  ) {

    def sendPublicKey(implicit monitor: SftpProgressMonitor): Future[Unit] = future {
      // Create the session
      val sftp = new SftpServer(server, port, username)
      val key = generateKeyPair(server, username)
      val session = sftp.createPublicKeySession(key)
      send(session)
    }

    def sendPassword(password: String)(implicit monitor: SftpProgressMonitor): Future[Unit] = future {
      // Create the session
      val sftp = new SftpServer(server, port, username)
      val session = sftp.createPasswordSession(password)
      send(session)
    }

    def send(session: SftpSession)(implicit monitor: SftpProgressMonitor) = {
      // Create the input stream
      val is = share.inputStream

      // Send the file
      try {
        session.connect
        session.cd(destination)
        session.put(share.name, is)
      } finally {
        session.disconnect
        is.close
      }
    }

    def start(password: Option[String]): Unit = {

      // If password is empty, ask
      if (auth == "password" && !password.isDefined) ask(password)

      // Else, try connecting
      else {

        // Create a monitor
        implicit val m = Monitor(share.size)

        // Show the spinner
        m.createSpinner

        // Send the file
        val fsend = auth match {
          case "password" => sendPassword(password.get)
          case "public_key" => sendPublicKey
          case _ => throw new Exception("Oops, wrong auth method!")
        }

        // Inform the user and finish the activity on success
        fsend onSuccess { case _ =>

          // Save password if the "remember" flag is set
          if (auth == "password" && shouldSavePassword)
            savePassword(password.get)

          // Notify the user and close the activity
          runOnUiThread {
            if (m.isComplete) {
              toast("Transfer successful")
              ctx.finish
            }

            else toast("Transfer cancelled")
          }
        }

        // Inform the user and go back to the activity on failure
        fsend onFailure { case e: Throwable =>
          runOnUiThread {
            // Send a toast and dismiss the spinner
            toast("Transfer failed: " + e.getMessage)

            // Dismiss monitor
            monitor.foreach(_.end)

            // Ask for the password again
            if (auth == "password") ask(password)
          }
        }
      }
    }

    def ask(previous: Option[String] = None): Unit = {
      InputDialog.show("Enter password", previous.getOrElse("")) {
        p => start(Some(p))
      }
    }
  }
}
