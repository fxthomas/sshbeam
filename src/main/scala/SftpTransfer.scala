package io.github.fxthomas.sshbeam

import com.jcraft.jsch._
import java.util.Properties
import java.io.File
import java.io.InputStream

case class HardcodedUserInfo(password: String) extends UserInfo {
  def getPassphrase = null
  def getPassword = password
  def promptPassword(s: String) = true
  def promptPassphrase(s: String) = true
  def promptYesNo(s: String) = true
  def showMessage(s: String) = System.out.println(s)
}

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

case class SftpServer (
  server: String,
  port: Int,
  username: String) {

  // JSch main object
  val jsch = new JSch

  /**
   * Create a new session with pubkey authentication
   */
  def createPublicKeySession(key: File) = {
    jsch.addIdentity(key.getAbsolutePath)
    new SftpSession(jsch.getSession(username, server, port))
  }

  /**
   * Create a new session with password authentication
   */
  def createPasswordSession(pass: String) = {
    val session = jsch.getSession(username, server, port)
    session.setUserInfo(HardcodedUserInfo(pass))
    new SftpSession(session)
  }
}
