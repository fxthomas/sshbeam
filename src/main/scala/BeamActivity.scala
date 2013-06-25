package io.github.fxthomas.sshbeam

import android.app._
import android.os._
import android.content._
import android.net._
import android.provider._
import android.preference._
import android.view._

import org.scaloid.common._

import scala.concurrent._
import scala.collection.JavaConversions._

import ExecutionContext.Implicits.global

import Sftp._
import Helpers._

class BeamActivity
extends SActivity
with SharedPreferences.OnSharedPreferenceChangeListener
with TypedActivity {

  // Cancel and send buttons
  def vcancel = findView(TR.cancel)
  def vsend = findView(TR.send)

  // Retrieve information from the intent
  def uri = Option(getIntent.getData) orElse Option(getIntent.getParcelableExtra(Intent.EXTRA_STREAM).asInstanceOf[Uri])
  def text = Option(getIntent.getStringExtra(Intent.EXTRA_TEXT))
  def subject = Option(getIntent.getStringExtra(Intent.EXTRA_SUBJECT))
  def mimeType = Option(getIntent.getType)

  // Preference fragment with the transfer configuration
  def beamParams =
    getFragmentManager.findFragmentById(R.id.params).asInstanceOf[BeamParams]

  def generateKey(onSuccess: SftpKey => Unit) {
    // Create the key
    beamParams.server.generatedKey match {
      case Some(k) => onSuccess(k)
      case None => {
        // Create the future and the dialog
        val key = future { beamParams.server.createKey }
        val d = spinnerDialog("SSH Beam", "Generating key...")

        // On success
        key onSuccess {
          case pk: SftpKey => {
            onSuccess(pk)
            runOnUiThread(d.dismiss)
          }
        }

        // On failure, show a toast
        key onFailure { case e: Exception =>
          e.printStackTrace
          toast(e.getMessage)
          runOnUiThread(d.dismiss)
        }
      }
    }
  }

  override def onCreate(bundle: Bundle) {
    // Create the activity
    super.onCreate(bundle)
    setContentView(R.layout.main)

    // Setup the beam preference fragment
    if (bundle == null) {
      getFragmentManager
      .beginTransaction
      .add(R.id.params, new BeamParams, "beam_params")
      .commit
    }

    // Do nothing if cancel is clicked
    vcancel.onClick(finish)

    // Send the file if send is clicked
    vsend onClick {
      beamParams.authMethod match {
        case "public_key" => generateKey { _ => createTransferIntent }
        case _ => createTransferIntent
      }
    }
  }

  override def onResume = {
    // Call super
    super.onResume

    // Register a pref change listener
    beamParams.getPreferenceManager
              .getSharedPreferences
              .registerOnSharedPreferenceChangeListener(this)

    // Check if we have something to share
    if (uri.isEmpty && text.isEmpty) {
      toast("Nothing to share")
      finish
    }

    // Set default filename
    beamParams.filename =
      uri.flatMap(_.dataName)
         .getOrElse(createFilename(mimeType, text, subject))
  }

  override def onPause = {
    // Call super
    super.onPause

    // Unregister the pref change listener
    beamParams.getPreferenceManager
              .getSharedPreferences
              .unregisterOnSharedPreferenceChangeListener(this)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.beam_menu, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.ui_sharekey => generateKey {
      pk: SftpKey => {
        val intent = new Intent
        intent.setAction(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, pk.publicKey)
        intent.setType("text/plain")
        startActivity(intent)
      }
    }

    return true
  }

  def onSharedPreferenceChanged(pref: SharedPreferences, key: String) {
    key match {
      case "ssh_auth_method" => beamParams.setupPreferences
      case k => ()
    }
  }

  def createTransferIntent = {

    // Create intent
    val intent = new Intent("io.github.fxthomas.sshbeam.Beam")
    intent.putExtra(BeamService.EXTRA_NAME, beamParams.filename)
    intent.putExtra(BeamService.EXTRA_DESTINATION, beamParams.destination)
    intent.putExtra(BeamService.EXTRA_SERVER, beamParams.server)
    intent.putExtra(BeamService.EXTRA_AUTH, beamParams.auth)

    // Add shared content
    uri match {
      case Some(u) => {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setData(u)
      }

      case None => {
        intent.putExtra(Intent.EXTRA_TEXT, text.get)
      }
    }

    // Start intent
    startService(intent); finish
  }
}
