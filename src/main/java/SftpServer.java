package io.github.fxthomas.sshbeam;

import android.os.Parcelable;
import android.os.Parcel;

public class SftpServer implements Parcelable {
  private String _server;
  private int _port;
  private String _username;

  public SftpServer(String server, int port, String username) {
    this._server = server;
    this._port = port;
    this._username = username;
  }

  private SftpServer(Parcel in) {
    this._server = in.readString();
    this._port = in.readInt();
    this._username = in.readString();
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel out, int flags) {
    out.writeString(_server);
    out.writeInt(_port);
    out.writeString(_username);
  }

  public static final Parcelable.Creator<SftpServer> CREATOR
    = new Parcelable.Creator<SftpServer>() {
      public SftpServer createFromParcel(Parcel in) {
        return new SftpServer(in);
      }

      public SftpServer[] newArray(int size) {
        return new SftpServer[size];
      }
    };

  public String username() {
    return _username;
  }

  public int port() {
    return _port;
  }

  public String server() {
    return _server;
  }
}
