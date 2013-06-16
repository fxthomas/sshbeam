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

import scala.concurrent._
import scala.collection.JavaConversions._

import ExecutionContext.Implicits.global

case class FileExistsException(filename: String) extends Exception(s"$filename already exists on the remote server")

class BeamParams extends PreferenceFragment {
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.params)
  }
}

class BeamActivity
extends SActivity
with TypedActivity
with SharedPreferences.OnSharedPreferenceChangeListener {

  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)
  lazy val vcancel = findView(TR.cancel)
  lazy val vsend = findView(TR.send)
  lazy val vprogress = findView(TR.progress)
  lazy val vcontent = findView(TR.content)

  implicit val context = this

  def filename = prefs.getString("ssh_transfer_filename", "")
  def destination = prefs.getString("ssh_transfer_destination", "")
  def server = prefs.getString("ssh_server_address", "")
  def port = prefs.getString("ssh_server_port", "22").toInt
  def username = prefs.getString("ssh_auth_username", "")
  def shouldSavePassword = prefs.getBoolean("ssh_auth_save_password", false)
  def password = if (shouldSavePassword) {
      Some(prefs.getString("ssh_auth_password", ""))
    } else None

  override def onCreate(bundle: Bundle) {
    // Create the activity
    super.onCreate(bundle)
    setContentView(R.layout.main)

    // Read the file URI from the intent
    val intent = getIntent
    val uri = Option(
      intent.getParcelableExtra(Intent.EXTRA_STREAM).asInstanceOf[Uri]) getOrElse {

      // If we can't find the URI, then somebody shared text directly.
      // In which case, we use that text to create a new file.
      val text = intent.getStringExtra(Intent.EXTRA_TEXT)

      // If the user shared _nothing_, send a toast and finish
      if (text == null) {
        toast("Nothing to share")
        finish
      }

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
        case _ => "txt"
      }

      // Create a temporary file
      val outdir = context.getCacheDir
      val outfile = new File(context.getCacheDir, s"${title}.${ext}")

      // Write to it
      val fs = new PrintStream(new FileOutputStream(outfile), true, "UTF-8")
      fs.print(text)
      fs.close

      // Return an URI
      Uri.fromFile(outfile)
    }

    // Register a pref change listener
    prefs.registerOnSharedPreferenceChangeListener(this)

    // Set the preferences
    val edit = prefs.edit
    edit.putString("ssh_transfer_filename", getFile(uri).map(_.getName).getOrElse(""))
    edit.commit

    // Setup the param list
    getFragmentManager.beginTransaction.add(R.id.params, new BeamParams).commit

    // Do nothing if cancel is clicked
    vcancel.onClick(finish)

    // Send the file if send is clicked
    vsend onClick {

      // Check if parameters are valid
      if (filename == "") toast("Destination filename can't be empty")
      else if (destination == "") toast("Destination directory can't be empty")
      else if (server == "") toast("Server can't be empty")
      else if (username == "") toast("Username can't be empty")
      else {

        // Prepare the transfer
        val transfer = Transfer(
            uri,
            filename,
            destination,
            server,
            port,
            username)

        // Try getting the password
        password match {

          // If we found it, start transferring
          case Some(p) => transfer.start(p)

          // If not, ask the user for his password
          case None => transfer.ask("")
        }
      }
    }
  }

  def savePassword(p: String) {
    val edit = prefs.edit
    edit.putString("ssh_auth_password", p)
    edit.commit
  }

  def clearPassword = savePassword("")

  def onSharedPreferenceChanged(pref: SharedPreferences, key: String) {
    if (key == "ssh_auth_save_password") clearPassword
  }

  case class Transfer(
    uri: Uri,
    filename: String,
    destination: String,
    server: String,
    port: Int,
    username: String
  ) {

    case class HardcodedUserInfo(password: String) extends UserInfo {
      def getPassphrase = null
      def getPassword = password
      def promptPassword(s: String) = true
      def promptPassphrase(s: String) = true
      def promptYesNo(s: String) = true
      def showMessage(s: String) = runOnUiThread(toast(s))
    }

    def send(password: String): Future[Unit] = future {

      // Create a session
      val session = (new JSch).getSession(username, server, port)
      session.setUserInfo(HardcodedUserInfo(password))

      // Configure the connection
      val config = new Properties
      config.setProperty("StrictHostKeyChecking", "no")
      session.setConfig(config)
      session.connect()

      // Open the SFTP channel and the input stream
      val is = getContentResolver.openInputStream(uri)
      val channel = session.openChannel("sftp").asInstanceOf[ChannelSftp]
      channel.connect
      channel.cd(destination)

      // Check if the file exists
      val exists: Boolean = try {
        channel.lstat(filename); true
      } catch {
        case e: SftpException if e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE => false
      }

      // If it exists, fail
      if (exists) throw FileExistsException(filename)

      // Transfer the file
      channel.put(is, filename)

      // Close everything
      channel.disconnect
      session.disconnect
      is.close
    }

    def start(password: String): Unit = {

      // If password is empty, ask
      if (password.isEmpty) ask("")

      // Else, try connecting
      else {

        // Show the loading spinner
        vprogress.setVisibility(View.VISIBLE)
        vcontent.setVisibility(View.GONE)

        // Send the file
        val fsend = send(password)

        // Inform the user and finish the activity on success
        fsend onSuccess { case _ =>

          // Save password if the "remember" flag is set
          if (shouldSavePassword) savePassword(password)

          // Notify the user and close the activity
          runOnUiThread {
            toast("Transfer successful")
            finish
          }
        }

        // Inform the user and go back to the activity on failure
        fsend onFailure { case e: Throwable =>
          runOnUiThread {
            // Send a toast
            toast("Transfer failed: " + e.getMessage)

            // Show the main UI
            vprogress.setVisibility(View.GONE)
            vcontent.setVisibility(View.VISIBLE)

            // Ask for the password again
            ask(password)
          }
        }
      }
    }

    def ask(previous: String = ""): Unit = {
      InputDialog.show("Enter password", previous) {
        p => start(p)
      }
    }
  }

  def getFile(contentUri: Uri): Option[File] = {
    if (contentUri.getScheme == "content") {
      val resolver = getContentResolver

      for (cr <- Option(resolver.query(
        contentUri, Array(MediaStore.MediaColumns.DATA),
        null, null, null))) {

        try {
          if (cr.moveToFirst) return Some(new File(cr.getString(0)))
        } finally {
          cr.close
        }
      }
    } else if (contentUri.getScheme == "file") {
      return Some(new File(contentUri.getLastPathSegment))
    }

    return None
  }
}
