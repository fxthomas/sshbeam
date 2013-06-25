package io.github.fxthomas.sshbeam

import android.os.Parcel
import android.os.Parcelable
import android.content.Context

import com.jcraft.jsch._

import java.util.Properties
import java.io.File
import java.io.InputStream

import scala.io.Source

case class FileExistsException(filename: String) extends Exception("File already exists on the remote server")

class SftpSession(session: Session) {
  // Configure the session
  val config = new Properties
  config.setProperty("StrictHostKeyChecking", "no")
  session.setConfig(config)

  // Channel
  var channel: Option[ChannelSftp] = None

  /**
   * Connect the session
   */
  def connect = {
    session.connect
    channel = Some(session.openChannel("sftp").asInstanceOf[ChannelSftp])
    channel.foreach(_.connect)
  }

  /**
   * Disconnect from the session
   */
  def disconnect = {
    session.disconnect
    channel.foreach(_.disconnect)
    channel = None
  }

  /**
   * Change directory
   */
  def cd(dir: String) = channel.foreach(_ cd dir)

  /**
   * Send a file through the session
   */
  def put(name: String, is: InputStream)(implicit monitor: SftpProgressMonitor) {

    // Retrieve channel
    val chan = channel.get

    // Check if the file exists
    val exists: Boolean = try {
      chan lstat name; true
    } catch {
      case e: SftpException if e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE => false
    }

    // If it exists, fail
    if (exists) throw FileExistsException(name)

    // Transfer the file
    chan.put(is, name, monitor)
  }
}

object Sftp {

  implicit class SftpKeyEx(val key: SftpKey) {
    def privateKey = Source.fromFile(key.privateKeyFile).mkString
    def publicKey = Source.fromFile(key.publicKeyFile).mkString
  }

  implicit class SftpServerEx(val server: SftpServer) {
    def canonicalName: String = {
      if (server.server == null || server.username == null ||
          server.server.isEmpty || server.username.isEmpty)
        throw MissingParameterException("Please configure your server address and username!")

      return s"${server.username}@${server.server}:${server.port}"
    }

    def createSession(auth: SftpAuth) = new SftpSession(auth.createSession(server))

    def generatedKey(implicit ctx: Context) = {
      val filename = canonicalName
      val fpriv = new File(ctx.getFilesDir, filename)
      val fpub = new File(ctx.getFilesDir, filename + ".pub")
      if (fpriv.exists && fpub.exists) Option(new SftpKey(fpriv, fpub))
      else None
    }

    def createKey(implicit ctx: Context) = {

      // Create filename
      val filename = canonicalName

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

      // Return the generated key
      generatedKey.get
    }
  }
}
