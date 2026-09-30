/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import android.webkit.WebView

/**
 * Name the hooks publish a taken URL under, read back with [WebView.evaluateJavascript].
 */
private const val CAPTURED_URL_JS = "window._morpheCapturedUrl||''"

/**
 * Installs the passive capture hooks in the document [WebView] currently shows, then hands
 * [onCaptured] whatever they already hold.
 *
 * The page keeps its own behaviour — nothing is clicked and no request is rewritten. What the
 * hooks add is the one thing the WebView's own callbacks cannot see: a file URL that the page's
 * script learns from an API response and never navigates to. That is how a mirror hands out a
 * signed CDN link, so the response *type* is read as well as its body.
 *
 * Must be called after each document is committed; hooks do not survive a new document.
 */
internal fun WebView.installCaptureHooks(onCaptured: (url: String?) -> Unit) {
    // Annotated because evaluateJavascript answers through a platform type, which would
    // otherwise leave the parameter of this lambda inferred as Any?
    evaluateJavascript(CAPTURE_HOOKS_JS) { _: String? ->
        readCapturedUrl(onCaptured)
    }
}

/**
 * Reads the URL the hooks hold, if any. Called on every load step, because the hooks can publish
 * a URL at a point where no callback of ours is due.
 */
internal fun WebView.readCapturedUrl(onCaptured: (url: String?) -> Unit) {
    evaluateJavascript(CAPTURED_URL_JS) { raw: String? ->
        onCaptured(raw?.trim()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" })
    }
}

/**
 * Marks a response as an archive by the type it declares, or by the name on its path.
 *
 * The type is what carries a signed CDN URL, which normally has no extension to read. The query
 * is dropped before the name is checked because a mirror puts its token there.
 */
private const val CAPTURE_HOOKS_JS = """
(function () {
  if (window._morpheHooksInstalled) return;
  window._morpheHooksInstalled = true;
  window._morpheCapturedUrl = '';

  function isArchive(u, ct) {
    if (ct) {
      var t = String(ct).split(';')[0].trim().toLowerCase();
      if (t === 'application/vnd.android.package-archive') return true;
      if (t.indexOf('application/vnd.android.package') === 0) return true;
      if (t.indexOf('application/x-apk') === 0) return true;
      if (t === 'application/octet-stream') return true;
    }
    if (!u || typeof u !== 'string') return false;
    var p = u.toLowerCase().split('#')[0].split('?')[0];
    return p.endsWith('.apk') || p.endsWith('.apkm') || p.endsWith('.xapk') ||
           p.endsWith('.apks') || p.endsWith('.aab');
  }

  function take(u, ct) {
    if (!window._morpheCapturedUrl && isArchive(u, ct)) {
      window._morpheCapturedUrl = u;
    }
  }

  var _fetch = window.fetch;
  if (_fetch) {
    window.fetch = function (input, init) {
      var url = (typeof input === 'string') ? input : (input && input.url);
      return _fetch.apply(this, arguments).then(function (response) {
        try { take(response.url || url, response.headers.get('content-type')); } catch (e) {}
        return response;
      });
    };
  }

  var _open = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    this._morpheUrl = url;
    return _open.apply(this, arguments);
  };

  var _send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    var xhr = this;
    xhr.addEventListener('load', function () {
      var ct = '';
      try { ct = xhr.getResponseHeader('content-type') || ''; } catch (e) {}
      take(xhr.responseURL || xhr._morpheUrl, ct);
      // The link itself can be the payload: a mirror answers an endpoint with the URL to use
      try {
        var j = JSON.parse(xhr.responseText || '{}');
        var d = j.url || j.download_url || j.downloadUrl || j.link || j.data;
        if (typeof d === 'string' && d.length > 10 && (d.indexOf('http') === 0 || d.indexOf('//') === 0)) {
          take(d.indexOf('//') === 0 ? 'https:' + d : d, ct);
        }
      } catch (e) {}
    });
    return _send.apply(this, arguments);
  };
})();
"""
