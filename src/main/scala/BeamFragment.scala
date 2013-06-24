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
    filename: String,
    destination: String,
    server: String,
    port: Int,
    username: String,
    auth: String,
    shouldSavePassword: Boolean,
    password: Option[String]) = {

    val intent = new Intent("io.github.fxthomas.sshbeam.BeamService")
    intent.putExtra("filename", filename)
    intent.putExtra("server", server)
    intent.putExtra("username", username)
    intent.putExtra("port", port)
    intent.putExtra("auth", auth)
    intent.putExtra("destination", destination)
    intent.putExtra("password", password getOrElse "")

    // This is YUCK. Rewrite that with Parcelable.
    share match {
      case UriSharedObject(uri) => {
        intent.putExtra("s_type", "uri")
        intent.putExtra("s_uri", uri)
      }

      case TextSharedObject(fname, contents) => {
        intent.putExtra("s_type", "text")
        intent.putExtra("s_fname", fname)
        intent.putExtra("s_contents", contents)
      }

      case _ => intent.putExtra("s_type", "s_unknown")
    }

    getActivity.startService(intent)
    getActivity.finish
  }
}
