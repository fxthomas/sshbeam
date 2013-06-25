package io.github.fxthomas.sshbeam;

import android.os.Parcelable;
import android.os.Parcel;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public class SftpPassword extends SftpAuth {
  private String _password;

  public SftpPassword(String password) {
    this._password = password;
  }

  private SftpPassword(Parcel in) {
    this._password = in.readString();
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel out, int flags) {
    out.writeString(_password);
  }

  public static final Parcelable.Creator<SftpPassword> CREATOR
    = new Parcelable.Creator<SftpPassword>() {
      public SftpPassword createFromParcel(Parcel in) {
        return new SftpPassword(in);
      }

      public SftpPassword[] newArray(int size) {
        return new SftpPassword[size];
      }
    };

  @Override
  public String getPassphrase() {
    return null;
  }

  @Override
  public String getPassword() {
    return _password;
  }

  public void setPassword(String passwd) {
    _password = passwd;
  }

  @Override
  public boolean promptPassphrase(String message) {
    return false;
  }

  @Override
  public boolean promptPassword(String message) {
    return true;
  }

  @Override
  public boolean promptYesNo(String message) {
    return true;
  }

  @Override
  public void showMessage(String message) { }

  public Session createSession(SftpServer server) throws JSchException {
    // Log the creation
    android.util.Log.i("SSH Beam", "Creating SFTP session (password)");

    // Create JSch object
    JSch jsch = new JSch();

    // Create session
    Session session = jsch.getSession(
      server.username(),
      server.server(),
      server.port()
    );

    // Set user passsword
    session.setUserInfo(this);

    // Return session
    return session;
  }
}
