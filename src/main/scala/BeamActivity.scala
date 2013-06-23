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

case class FileExistsException(filename: String) extends Exception(s"$filename already exists on the remote server")
case class MissingParameterException(message: String) extends Exception(message)

class BeamActivity
extends SActivity
with TypedActivity
with SharedPreferences.OnSharedPreferenceChangeListener {

  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)
  lazy val vcancel = findView(TR.cancel)
  lazy val vsend = findView(TR.send)
  lazy val vprogress = findView(TR.progress)
  lazy val vcontent = findView(TR.content)

  object BeamParams extends PreferenceFragment {
    override def onCreate(savedInstanceState: Bundle) = {
      super.onCreate(savedInstanceState)
      addPreferencesFromResource(R.xml.params)
      setupPreferences
    }
  }

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

  def authMethod = prefs.getString("ssh_auth_method", "password")

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.beam_menu, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.ui_sharekey => {

      // Show a toast if the configuration is bad
      if (server == null || username == null || server.isEmpty || username.isEmpty) {
        toast("Please configure your server address and username!")
        return true
      }

      // Load the public key
      val pubkey: Future[String] = future {
        val kf = generateKeyPair(server, username)
        val ki = openFileInput(kf.getName + ".pub")
        val pubkey = Source.fromInputStream(ki).mkString
        ki.close
        pubkey
      }

      // Show the spinner dialog
      val dlg = spinnerDialog("SSH Beam", "Generating key pair...")

      // Share it
      pubkey onSuccess {
        case key: String => runOnUiThread {
          val intent = new Intent
          intent.setAction(Intent.ACTION_SEND)
          intent.putExtra(Intent.EXTRA_TEXT, key)
          intent.setType("text/plain")
          runOnUiThread(dlg.dismiss)
          startActivity(intent)
        }
      }

      // If the generation failed, display a toast
      pubkey onFailure {
        case exc: MissingParameterException =>
          runOnUiThread(dlg.dismiss)
          toast(exc.getMessage)
      }
    }

    return true
  }

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
    if (authMethod == null) edit.putString("ssh_auth_method", "password")
    edit.commit

    // Setup the param list
    getFragmentManager.beginTransaction.add(R.id.params, BeamParams).commit

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
            username,
            authMethod)

        // Start the transfer
        transfer.start(password)
      }
    }
  }

  def savePassword(p: String) {
    val edit = prefs.edit
    edit.putString("ssh_auth_password", p)
    edit.commit
  }

  def clearPassword = savePassword("")

  def enablePasswordPref(b: Boolean) =
    for (p <- Option(BeamParams.findPreference("ssh_auth_save_password")))
      p.setEnabled(b)

  def generateKeyPair(server: String, username: String) = {
    // Preflight checks
    if (server == null || username == null || server.isEmpty || username.isEmpty)
      throw MissingParameterException("Please configure your server address and username!")

    // Generate a canonical name for the key pair
    val fserver = "[^\\w]+".r.replaceAllIn(server, "_")
    val fusername = "[^\\w]+".r.replaceAllIn(username, "_")
    val filename = s"$fserver-$fusername"

    // If the key isn't generated, generate it, write it and return it
    if (!(fileList contains filename)) {

      // Generate the key
      val key = KeyPair.genKeyPair(new JSch, KeyPair.DSA)

      // Write private key
      val fpriv = openFileOutput(filename, Context.MODE_PRIVATE)
      key.writePrivateKey(fpriv)
      fpriv.close

      // Write public key
      val fpub = openFileOutput(filename + ".pub", Context.MODE_PRIVATE)
      key.writePublicKey(fpub, "sshbeam@android")
      fpub.close
    }

    // Return the name of the private key file
    new File(getFilesDir, filename)
  }

  def setupPreferences = {
    authMethod match {
      case "public_key" => enablePasswordPref(false)
      case "password" => enablePasswordPref(true)
      case _ => throw new Exception("Ooops, wrong auth_method")
    }
  }

  def onSharedPreferenceChanged(pref: SharedPreferences, key: String) {
    key match {
      case "ssh_auth_save_password" => clearPassword
      case "ssh_auth_method" => setupPreferences
      case _ => ()
    }
  }

  case class Transfer(
    uri: Uri,
    filename: String,
    destination: String,
    server: String,
    port: Int,
    username: String,
    auth: String
  ) {

    case class HardcodedUserInfo(password: String) extends UserInfo {
      def getPassphrase = null
      def getPassword = password
      def promptPassword(s: String) = true
      def promptPassphrase(s: String) = true
      def promptYesNo(s: String) = true
      def showMessage(s: String) = toast(s)
    }

    def sendPublicKey: Future[Unit] = future {
      val jsch = new JSch
      jsch.addIdentity(generateKeyPair(server, username).getAbsolutePath)
      send(jsch.getSession(username, server, port))
    }

    def sendPassword(password: String): Future[Unit] = future {
      val session = (new JSch).getSession(username, server, port)
      session.setUserInfo(HardcodedUserInfo(password))
      send(session)
    }

    def send(session: Session) {

      // Write something in the logs
      info(s"Starting SFTP transfer ($auth)")

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

    def start(password: Option[String]): Unit = {

      // If password is empty, ask
      if (auth == "password" && !password.isDefined) ask(password)

      // Else, try connecting
      else {

        // Show the loading spinner
        val dlg = spinnerDialog("SSH Beam", "Transfer in progress...")

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
            dlg.dismiss
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
            dlg.dismiss

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
