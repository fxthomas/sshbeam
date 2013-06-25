package io.github.fxthomas.sshbeam;

import android.os.Parcelable;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

public abstract class SftpAuth implements Parcelable, UserInfo {
  public abstract Session createSession(SftpServer server) throws JSchException;
}
