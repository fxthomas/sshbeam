package io.github.fxthomas.sshbeam

import android.app._
import android.os._
import android.content._
import android.net._
import android.provider._
import android.preference._
import android.view._
import android.webkit.WebView

import org.scaloid.common._

class BeamHelpActivity extends SActivity with TypedActivity {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    getWindow requestFeature Window.FEATURE_NO_TITLE

    val webView = new WebView(this)
    webView loadUrl "file:///android_asset/help.html"
    setContentView(webView)
  }
}
