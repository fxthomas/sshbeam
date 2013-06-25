package io.github.fxthomas.sshbeam

import android.os.Bundle
import android.content.SharedPreferences
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.TwoStatePreference
import android.preference.PreferenceFragment

import Sftp._

class BeamParams extends PreferenceFragment {

  implicit def ctx = getActivity

  override def onCreate(savedInstanceState: Bundle) = {
    // Prepare the layout
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.params)
  }

  override def onResume = {
    // Call super
    super.onResume

    // Set default preferences
    setupPreferences
  }

  def getTextValue(key: String) = {
    findPreference(key)
    .asInstanceOf[EditTextPreference]
    .getText
  }

  def getListValue(key: String) = {
    findPreference(key)
    .asInstanceOf[ListPreference]
    .getValue
  }

  def getBooleanValue(key: String) = {
    findPreference(key)
    .asInstanceOf[TwoStatePreference]
    .isChecked
  }

  def setTextValue(key: String, value: String) = {
    findPreference(key)
    .asInstanceOf[EditTextPreference]
    .setText(value)
  }

  def setListValue(key: String, value: String) = {
    findPreference(key)
    .asInstanceOf[ListPreference]
    .setValue(value)
  }

  def setBooleanValue(key: String, value: Boolean) = {
    findPreference(key)
    .asInstanceOf[TwoStatePreference]
    .setChecked(value)
  }

  def authMethod = getListValue("ssh_auth_method")

  def auth: SftpAuth = {
    authMethod match {
      case "password" => new SftpPassword(getTextValue("ssh_auth_password"))
      case "public_key" => server.generatedKey.get
      case _ => throw new Exception("Ooops, wrong auth_method")
    }
  }

  def server = new SftpServer(
    getTextValue("ssh_server_address"),
    getTextValue("ssh_server_port").toInt,
    getTextValue("ssh_auth_username")
  )

  def destination = getTextValue("ssh_transfer_destination")

  def filename = getTextValue("ssh_transfer_filename")
  def filename_=(s: String) = setTextValue("ssh_transfer_filename", s)

  def enablePasswordPref =
    findPreference("ssh_auth_password").setEnabled _

  def setupPreferences = {
    authMethod match {
      case "public_key" => enablePasswordPref(false)
      case "password" => enablePasswordPref(true)
      case _ => throw new Exception("Ooops, wrong auth_method")
    }
  }
}
