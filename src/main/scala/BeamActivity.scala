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
with SharedPreferences.OnSharedPreferenceChangeListener
with TypedActivity {

  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)
  lazy val vcancel = findView(TR.cancel)
  lazy val vsend = findView(TR.send)
  lazy val vprogress = findView(TR.progress)
  lazy val vcontent = findView(TR.content)
  lazy val share = SharedObject(getIntent)

  case class Monitor(spinner: ProgressDialog) extends SftpProgressMonitor {

    var size = 1L

    def end = runOnUiThread(spinner.dismiss)

    def count(cnt: Long): Boolean = {
      runOnUiThread { spinner incrementProgressBy cnt.toInt }
      return true
    }

    def init(op: Int, src: String, dest: String, max: Long) {
      runOnUiThread {
        spinner setProgress 0
        spinner setMax size.toInt
        spinner show
      }
    }
  }

  def beamParams =
    getFragmentManager.findFragmentById(R.id.params).asInstanceOf[BeamParams]

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.beam_menu, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.ui_sharekey => {

      // Preflight checks
      if (beamParams.server == null || beamParams.username == null ||
          beamParams.server.isEmpty || beamParams.username.isEmpty)
        return true

      // Load the public key
      val pubkey: Future[String] = future {
        val kf = generateKeyPair(beamParams.server, beamParams.username)
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

    // Check if the shared file/content is valid
    if (!share.isDefined) {
      toast("Nothing to share")
      finish
    }

    if (bundle == null) {
      // Display some info
      info("Sharing URI " + getIntent.getParcelableExtra(Intent.EXTRA_STREAM).asInstanceOf[Uri] +
              " (type = " + getIntent.getType + ")")

      // Setup the param list
      getFragmentManager.beginTransaction.add(R.id.params, new BeamParams, "beam_params").commit
    }

    // Do nothing if cancel is clicked
    vcancel.onClick(finish)

    // Send the file if send is clicked
    vsend onClick {

      // Check if parameters are valid
      if (beamParams.filename == "") toast("Destination filename can't be empty")
      else if (beamParams.destination == "") toast("Destination directory can't be empty")
      else if (beamParams.server == "") toast("Server can't be empty")
      else if (beamParams.username == "") toast("Username can't be empty")
      else {

        // Set the filename
        share.foreach(_.name = beamParams.filename)

        // Prepare the transfer
        val transfer = Transfer(
            share.get,
            beamParams.destination,
            beamParams.server,
            beamParams.port,
            beamParams.username,
            beamParams.authMethod)

        // Start the transfer
        transfer.start(password)
      }
    }
  }

  override def onResume = {
    // Call super
    super.onResume

    // Set default filename
    beamParams.filename = share.map(_.name) getOrElse ("untitled.txt")

    // Register a pref change listener
    beamParams.getPreferenceManager
              .getSharedPreferences
              .registerOnSharedPreferenceChangeListener(this)
  }

  override def onPause = {
    // Call super
    super.onPause

    // Unregister the pref change listener
    beamParams.getPreferenceManager
              .getSharedPreferences
              .unregisterOnSharedPreferenceChangeListener(this)
  }

  def generateKeyPair(server: String, username: String) = {
    // Preflight checks
    if (beamParams.server == null || beamParams.username == null ||
        beamParams.server.isEmpty || beamParams.username.isEmpty)
      throw MissingParameterException("Please configure your server address and username!")

    // Generate a canonical name for the key pair
    val fserver = "[^\\w]+".r.replaceAllIn(beamParams.server, "_")
    val fusername = "[^\\w]+".r.replaceAllIn(beamParams.username, "_")
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

  case class Transfer(
    share: SharedObject,
    destination: String,
    server: String,
    port: Int,
    username: String,
    auth: String
  ) {

    val key = auth match {
      case "password" => None
      case "public_key" => {
        val dlg = spinnerDialog("SSH Beam", "Generating key pair...")
        val key = future { generateKeyPair(server, username) }
        key onComplete { case _ => runOnUiThread(dlg.dismiss) }
        Some(key)
      }
    }

    case class HardcodedUserInfo(password: String) extends UserInfo {
      def getPassphrase = null
      def getPassword = password
      def promptPassword(s: String) = true
      def promptPassphrase(s: String) = true
      def promptYesNo(s: String) = true
      def showMessage(s: String) = toast(s)
    }

    def sendPublicKey(keyfile: File, monitor: Monitor): Future[Unit] = future {
      val jsch = new JSch
      jsch.addIdentity(keyfile.getAbsolutePath)
      send(jsch.getSession(username, server, port), monitor)
    }

    def sendPassword(password: String, monitor: Monitor): Future[Unit] = future {
      val session = (new JSch).getSession(username, server, port)
      session.setUserInfo(HardcodedUserInfo(password))
      send(session, monitor)
    }

    def send(session: Session, monitor: Monitor) {

      // Write something in the logs
      info(s"Starting SFTP transfer ($auth)")

      // Configure the connection
      val config = new Properties
      config.setProperty("StrictHostKeyChecking", "no")
      session.setConfig(config)
      session.connect()

      // Set monitor size
      monitor.size = share.size

      // Open the SFTP channel and the input stream
      val channel = session.openChannel("sftp").asInstanceOf[ChannelSftp]
      channel.connect
      channel.cd(destination)

      // Check if the file exists
      val exists: Boolean = try {
        channel.lstat(share.name); true
      } catch {
        case e: SftpException if e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE => false
      }

      // If it exists, fail
      if (exists) throw FileExistsException(share.name)

      // Transfer the file
      val is = share.inputStream
      channel.put(is, share.name, monitor)

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

        // Create a spinner
        val spinner = new ProgressDialog(ctx)
        spinner setProgressStyle ProgressDialog.STYLE_HORIZONTAL
        spinner setTitle "SSH Beam"
        spinner setMessage "Transfer in progress..."
        spinner setIndeterminate false
        spinner setMax 100

        // Create a monitor
        val monitor = Monitor(spinner)

        // Send the file
        val fsend = auth match {
          case "password" => sendPassword(password.get, monitor)
          case "public_key" => key.get.flatMap(sendPublicKey(_, monitor))
          case _ => throw new Exception("Oops, wrong auth method!")
        }

        // Inform the user and finish the activity on success
        fsend onSuccess { case _ =>

          // Save password if the "remember" flag is set
          if (auth == "password" && beamParams.shouldSavePassword)
            savePassword(password.get)

          // Notify the user and close the activity
          runOnUiThread {
            spinner.dismiss
            toast("Transfer successful")
            finish
          }
        }

        // Inform the user and go back to the activity on failure
        fsend onFailure { case e: Throwable =>
          runOnUiThread {
            // Send a toast and dismiss the spinner
            toast("Transfer failed: " + e.getMessage)
            spinner.dismiss

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

  def savePassword(p: String) = {
    val edit = prefs.edit
    edit.putString("ssh_auth_password", p)
    edit.commit
  }

  def clearPassword = savePassword("")

  def password = if (beamParams.shouldSavePassword) {
    Some(prefs.getString("ssh_auth_password", null))
  } else None

  def onSharedPreferenceChanged(pref: SharedPreferences, key: String) {
    key match {
      case "ssh_auth_method" => beamParams.setupPreferences
      case k => ()
    }
  }
}
