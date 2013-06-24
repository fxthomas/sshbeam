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

  def beamParams =
    getFragmentManager.findFragmentById(R.id.params).asInstanceOf[BeamParams]

  def beamTransfer =
    getFragmentManager.findFragmentByTag("beam_transfer").asInstanceOf[BeamTransferFragment]

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

      beamTransfer.generatePublicKey(beamParams.server, beamParams.username) {
        pk => {
          val intent = new Intent
          intent.setAction(Intent.ACTION_SEND)
          intent.putExtra(Intent.EXTRA_TEXT, pk)
          intent.setType("text/plain")
          startActivity(intent)
        }
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

      // Setup fragments for this activity
      getFragmentManager.beginTransaction
                        .add(R.id.params, new BeamParams, "beam_params")
                        .add(new BeamTransferFragment, "beam_transfer")
                        .commit
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

        // Prepare the transfer
        val transfer = beamTransfer.transfer(
            share.get,
            beamParams.filename,
            beamParams.destination,
            beamParams.server,
            beamParams.port,
            beamParams.username,
            beamParams.authMethod,
            beamParams.shouldSavePassword,
            password)
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
