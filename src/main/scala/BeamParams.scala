package io.github.fxthomas.sshbeam

import android.os.Bundle
import android.content.SharedPreferences
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.TwoStatePreference
import android.preference.PreferenceFragment

class BeamParams extends PreferenceFragment {
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


  def filename = getTextValue("ssh_transfer_filename")
  def authMethod = getListValue("ssh_auth_method")
  def destination = getTextValue("ssh_transfer_destination")
  def server = getTextValue("ssh_server_address")
  def port = getTextValue("ssh_server_port").toInt
  def username = getTextValue("ssh_auth_username")
  def shouldSavePassword = getBooleanValue("ssh_auth_save_password")

  def filename_= = setTextValue("ssh_transfer_filename", _: String)
  def authMethod_= = setListValue("ssh_auth_method", _: String)
  def destination_= = setTextValue("ssh_transfer_destination", _: String)
  def server_= = setTextValue("ssh_server_address", _: String)
  def port_=(p: Int) = setTextValue("ssh_server_port", p.toString)
  def username_= = setTextValue("ssh_auth_username", _: String)
  def shouldSavePassword_= = setBooleanValue("ssh_auth_save_password", _: Boolean)

  def enablePasswordPref =
    findPreference("ssh_auth_save_password").setEnabled _

  def setupPreferences = {
    authMethod match {
      case "public_key" => enablePasswordPref(false)
      case "password" => enablePasswordPref(true)
      case _ => throw new Exception("Ooops, wrong auth_method")
    }
  }
}
