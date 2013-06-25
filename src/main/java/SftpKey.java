package io.github.fxthomas.sshbeam;

import java.io.File;
import android.os.Parcelable;
import android.os.Parcel;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SftpKey extends SftpAuth {
  private File _privateKeyFile;
  private File _publicKeyFile;

  public SftpKey(File privateKeyFile, File publicKeyFile) {
    this._privateKeyFile = privateKeyFile;
    this._publicKeyFile = publicKeyFile;
  }

  private SftpKey(Parcel in) {
    this._privateKeyFile = new File(in.readString());
    this._publicKeyFile = new File(in.readString());
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel out, int flags) {
    out.writeString(_privateKeyFile.getAbsolutePath());
    out.writeString(_publicKeyFile.getAbsolutePath());
  }

  public static final Parcelable.Creator<SftpKey> CREATOR
    = new Parcelable.Creator<SftpKey>() {
      public SftpKey createFromParcel(Parcel in) {
        return new SftpKey(in);
      }

      public SftpKey[] newArray(int size) {
        return new SftpKey[size];
      }
    };

  @Override
  public String getPassphrase() {
    return null;
  }

  @Override
  public String getPassword() {
    return null;
  }

  public void setPassword(String passwd) { }

  @Override
  public boolean promptPassphrase(String message) {
    return false;
  }

  @Override
  public boolean promptPassword(String message) {
    return false;
  }

  @Override
  public boolean promptYesNo(String message) {
    return true;
  }

  @Override
  public void showMessage(String message) { }

  public File privateKeyFile() {
    return _privateKeyFile;
  }

  public File publicKeyFile() {
    return _publicKeyFile;
  }

  public Session createSession(SftpServer server) throws JSchException {
    // Log the creation
    android.util.Log.i("SSH Beam", "Creating SFTP session (public_key)");

    // Create the JSch object
    JSch jsch = new JSch();

    // Add the key
    jsch.addIdentity(
      _privateKeyFile.getAbsolutePath(),
      _publicKeyFile.getAbsolutePath()
    );

    // Create a new session
    return jsch.getSession(
      server.username(),
      server.server(),
      server.port()
    );
  }
}
