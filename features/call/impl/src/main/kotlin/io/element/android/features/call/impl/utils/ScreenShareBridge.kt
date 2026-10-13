/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.element.android.features.call.impl.services.ScreenCaptureService

/**
 * Screen sharing for Element Call, which Android's WebView can't do by itself:
 *
 * - It gives the page a working `navigator.mediaDevices.getDisplayMedia()` (the WebView has none, and Element
 *   Call hides its "Share screen" button without it). The replacement asks the app to start a screen capture
 *   ([onStartRequested]/[onStopRequested]), draws the JPEG frames [ScreenCaptureService] produces onto a canvas
 *   and returns the canvas' `captureStream()`, which Element Call publishes like any other screen-share track.
 * - It turns Element Call's reactions button into a combined "reactions / share screen" button: tapping it asks
 *   the app to show its own sheet ([onMenuRequested]), which then calls [openReactions] or [toggleShare].
 *   Element Call's own share button is too wide for phones and is hidden by its stylesheet anyway.
 *
 * The script is also inlined in the call page's index.html, so that it exists before Element Call starts;
 * keep both copies in sync.
 */
class ScreenShareBridge(
    private val webView: WebView,
    private val onStartRequested: () -> Unit,
    private val onStopRequested: () -> Unit,
    private val onMenuRequested: (isSharing: Boolean) -> Unit,
) : ScreenCaptureService.Listener {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun start() {
        mainHandler.post(onStartRequested)
    }

    @JavascriptInterface
    fun stop() {
        mainHandler.post(onStopRequested)
    }

    @JavascriptInterface
    fun openMenu(isSharing: Boolean) {
        mainHandler.post { onMenuRequested(isSharing) }
    }

    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.w("ScreenShare", message)
    }

    /** Opens Element Call's own reactions picker. */
    fun openReactions() = eval("window.__openReactions && window.__openReactions();")

    /** Starts or stops sharing, like tapping Element Call's own share button. */
    fun toggleShare() = eval("window.__toggleShare && window.__toggleShare();")

    override fun onStarted(width: Int, height: Int) {
        eval("window.__screenShareStarted && window.__screenShareStarted($width, $height);")
    }

    override fun onFrame(jpegBase64: String) {
        eval("window.__screenShareFrame && window.__screenShareFrame('$jpegBase64');")
    }

    override fun onStopped() {
        eval("window.__screenShareEnded && window.__screenShareEnded();")
    }

    private fun eval(script: String) {
        mainHandler.post { webView.evaluateJavascript(script, null) }
    }

    companion object {
        private const val BRIDGE_NAME = "screenShareBridge"

        fun install(webView: WebView, bridge: ScreenShareBridge) {
            webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        }

        /** Idempotent: safe to evaluate again after every page load. */
        fun script(): String = """
            (function () {
              if (window.__screenSharePolyfilled) return;
              window.__screenSharePolyfilled = true;
              var devices = navigator.mediaDevices;
              if (!devices) return;

              var canvas = null, ctx = null, stream = null, track = null, pending = null, lastImage = null, heartbeat = null;
              var root = document.documentElement;

              function cleanup() {
                if (heartbeat) { clearInterval(heartbeat); heartbeat = null; }
                canvas = ctx = stream = track = lastImage = null;
                root.removeAttribute('data-screen-sharing');
              }

              window.__screenShareStarted = function (w, h) {
                if (canvas && !pending) {
                  // The phone rotated: same stream, new picture size.
                  canvas.width = w; canvas.height = h; lastImage = null;
                  ctx = canvas.getContext('2d');
                  ctx.fillStyle = '#000'; ctx.fillRect(0, 0, w, h);
                  return;
                }
                if (!pending) return;
                canvas = document.createElement('canvas');
                canvas.width = w; canvas.height = h;
                ctx = canvas.getContext('2d');
                ctx.fillStyle = '#000'; ctx.fillRect(0, 0, w, h);
                stream = canvas.captureStream(15);
                track = stream.getVideoTracks()[0];
                // LiveKit stops the track when sharing is turned off: stop the capture with it.
                var originalStop = track.stop.bind(track);
                track.stop = function () {
                  originalStop();
                  if (track) { try { screenShareBridge.stop(); } catch (e) {} }
                  cleanup();
                };
                // A still screen produces no new frames; repaint now and then so the stream keeps flowing.
                heartbeat = setInterval(function () {
                  if (ctx && lastImage) { try { ctx.drawImage(lastImage, 0, 0, canvas.width, canvas.height); } catch (e) {} }
                }, 500);
                root.setAttribute('data-screen-sharing', '1');
                var p = pending; pending = null;
                p.resolve(stream);
              };

              window.__screenShareFrame = function (b64) {
                if (!ctx) return;
                var img = new Image();
                img.onload = function () {
                  if (!ctx) return;
                  lastImage = img;
                  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                };
                img.src = 'data:image/jpeg;base64,' + b64;
              };

              window.__screenShareEnded = function () {
                if (pending) {
                  var p = pending; pending = null;
                  p.reject(new DOMException('Permission denied', 'NotAllowedError'));
                  return;
                }
                if (track) {
                  var t = track;
                  cleanup();
                  try { t.dispatchEvent(new Event('ended')); } catch (e) {}
                }
              };

              devices.getDisplayMedia = function () {
                return new Promise(function (resolve, reject) {
                  if (pending || track) { reject(new DOMException('Already sharing', 'InvalidStateError')); return; }
                  if (typeof screenShareBridge === 'undefined') { reject(new DOMException('Not supported', 'NotSupportedError')); return; }
                  pending = { resolve: resolve, reject: reject };
                  screenShareBridge.start();
                });
              };

              // ---- Combined "reactions / share screen" button ----
              function shareButton() { return document.querySelector('[data-testid="incall_screenshare"]'); }
              // The reactions button is the one right after the share button in the footer.
              function reactionsButton() {
                var share = shareButton();
                if (!share) return null;
                var footer = share.closest('[data-testid="footer-container"]');
                if (!footer) return null;
                var all = footer.querySelectorAll('button');
                for (var i = 0; i < all.length; i++) {
                  if (all[i] === share) return all[i + 1] || null;
                }
                return null;
              }
              window.__toggleShare = function () { var b = shareButton(); if (b) b.click(); };
              var bypass = false;
              window.__openReactions = function () {
                var b = reactionsButton();
                if (!b) return;
                bypass = true;
                b.click();
                setTimeout(function () { bypass = false; }, 400);
              };

              var lastOpen = 0;
              function comboHandler(e) {
                if (bypass) return;
                var button = e.target && e.target.closest && e.target.closest('button[data-combo]');
                if (!button) return;
                if (e.cancelable) { e.preventDefault(); }
                e.stopPropagation();
                e.stopImmediatePropagation();
                if (e.type === 'pointerup' || e.type === 'touchend' || e.type === 'click') {
                  var now = Date.now();
                  if (now - lastOpen > 500) {
                    lastOpen = now;
                    try { screenShareBridge.openMenu(!!track); } catch (err) {}
                  }
                }
              }
              ['pointerdown', 'mousedown', 'pointerup', 'touchend', 'click'].forEach(function (type) {
                document.addEventListener(type, comboHandler, true);
              });

              function icon(screenColor) {
                var svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64' fill='none' stroke='#e8eaed' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'>"
                  + "<path d='M13 51 L51 13' stroke='#4a5058' stroke-width='1.8'/>"
                  + "<circle cx='22' cy='22' r='7.5'/><path d='M18.8 24.2c.8 1.1 1.8 1.7 3.2 1.7s2.4-.6 3.2-1.7'/><path d='M19.3 20.2h.01M24.7 20.2h.01'/>"
                  + "<rect x='37' y='38' width='15' height='10' rx='1.6' stroke='" + screenColor + "'/><path d='M41 52h7' stroke='" + screenColor + "'/></svg>";
                return 'url("data:image/svg+xml,' + encodeURIComponent(svg) + '")';
              }
              var style = document.createElement('style');
              style.textContent =
                '[data-testid="incall_screenshare"]{display:none !important;}' +
                'button[data-combo]{position:relative !important;}' +
                'button[data-combo] svg{opacity:0 !important;}' +
                'button[data-combo]::after{content:"";position:absolute;left:0;top:0;right:0;bottom:0;pointer-events:none;' +
                  'background:' + icon('#e8eaed') + ' center/80% no-repeat;}' +
                'html[data-screen-sharing] button[data-combo]{background:#1f3d2f !important;}' +
                'html[data-screen-sharing] button[data-combo]::after{background:' + icon('#5dd39e') + ' center/80% no-repeat;}';
              (document.head || root).appendChild(style);

              function markCombo() {
                var b = reactionsButton();
                if (b && !b.hasAttribute('data-combo')) b.setAttribute('data-combo', '1');
              }
              function startObserver() {
                new MutationObserver(function () { try { markCombo(); } catch (e) {} })
                  .observe(root, { subtree: true, childList: true });
                markCombo();
              }
              if (document.body) { startObserver(); } else { document.addEventListener('DOMContentLoaded', startObserver); }

              try { screenShareBridge.log('screen share script ready'); } catch (e) {}
            })();
        """.trimIndent()
    }
}
