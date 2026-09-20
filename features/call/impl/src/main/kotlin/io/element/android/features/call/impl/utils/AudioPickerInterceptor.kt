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

/**
 * Element Call's own audio output button opens a small popup with "Handset" / "Loudspeaker". This
 * intercepts taps on that button (in the capture phase, so the web UI never sees them) and asks the
 * native app to show its own full-width picker instead.
 *
 * The button is recognised by its accessible label, which is one of Element Call's translations of
 * "Handset" / "Loudspeaker" (its label flips between the two depending on the current mode).
 *
 * It also restyles that button to match the real output (phone / speaker / headphones icon, white
 * background for speaker and headset). This is done purely with a data attribute on the button plus a
 * style sheet, never by adding/removing/replacing elements the page's own framework manages, since
 * that makes Element Call crash with "Something went wrong".
 */
class AudioPickerInterceptor(
    private val onOpenPicker: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun open() {
        mainHandler.post(onOpenPicker)
    }

    companion object {
        private const val BRIDGE_NAME = "audioPickerBridge"

        // Element Call's translations of settings.devices.handset / settings.devices.loudspeaker.
        private val LABELS = listOf(
            "Altavoz", "Altoparlante", "Cornetta", "Difuzor", "Dispositivo", "Głośnomówiący", "Handset", "Högtalare",
            "Højttaler", "Kaiutin", "Klausule", "Lautsprecher", "Loudspeaker", "Luuri", "Ohrhörer", "Pengeras suara",
            "Ponsel", "Reproduktor", "Skaļrunis", "Sluchátko", "Slúchadlo", "Słuchawka", "Telefon", "Telefonlur",
            "Valjuhääldi", "Гарнітура", "Громкоговоритель", "Гучномовець", "Динамик телефона", "听筒", "扬声器",
        )

        fun install(webView: WebView, interceptor: AudioPickerInterceptor) {
            webView.addJavascriptInterface(interceptor, BRIDGE_NAME)
        }

        /** Idempotent: safe to evaluate again after every page load. */
        fun script(): String {
            val labels = LABELS.joinToString(",") { "\"${it.lowercase()}\"" }
            return """
                (function () {
                  if (window.__audioPickerIntercepted) return;
                  window.__audioPickerIntercepted = true;
                  var labels = [$labels];
                  function labelOf(button) {
                    var text = button.getAttribute('aria-label');
                    if (!text) {
                      var ref = button.getAttribute('aria-labelledby');
                      var el = ref && document.getElementById(ref);
                      text = el ? el.textContent : '';
                    }
                    return (text || '').trim().toLowerCase();
                  }
                  function handler(e) {
                    var button = e.target && e.target.closest && e.target.closest('button');
                    if (!button || labels.indexOf(labelOf(button)) === -1) return;
                    e.preventDefault();
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    if (e.type === 'click') { $BRIDGE_NAME.open(); }
                  }
                  ['pointerdown', 'mousedown', 'click'].forEach(function (type) {
                    document.addEventListener(type, handler, true);
                  });

                  // Restyle the button (icon + colours) with CSS only; see the class docs for why.
                  function icon(path) {
                    var svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path d='" + path + "'/></svg>";
                    return 'url("data:image/svg+xml,' + encodeURIComponent(svg) + '")';
                  }
                  var PHONE = "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z";
                  var SPEAKER = "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z";
                  var HEADSET = "M12 3a9 9 0 0 0-9 9v7c0 1.1.9 2 2 2h4v-8H5v-1c0-3.87 3.13-7 7-7s7 3.13 7 7v1h-4v8h4c1.1 0 2-.9 2-2v-7a9 9 0 0 0-9-9z";
                  var style = document.createElement('style');
                  style.textContent =
                    'button[data-audio-kind]{position:relative !important;}' +
                    'button[data-audio-kind] svg{opacity:0 !important;}' +
                    'button[data-audio-kind]::after{content:"";position:absolute;left:50%;top:50%;width:24px;height:24px;' +
                      'transform:translate(-50%,-50%);background-color:currentColor;pointer-events:none;' +
                      '-webkit-mask:var(--audio-icon) center/contain no-repeat;mask:var(--audio-icon) center/contain no-repeat;}' +
                    'button[data-audio-kind="earpiece"]{--audio-icon:' + icon(PHONE) + ';}' +
                    'button[data-audio-kind="speaker"]{--audio-icon:' + icon(SPEAKER) + ';background:#eceef2 !important;color:#1b1d22 !important;}' +
                    'button[data-audio-kind="headset"]{--audio-icon:' + icon(HEADSET) + ';background:#eceef2 !important;color:#1b1d22 !important;}';
                  document.head.appendChild(style);

                  var kind = null;
                  function apply() {
                    if (!kind) return;
                    var buttons = document.querySelectorAll('button');
                    for (var i = 0; i < buttons.length; i++) {
                      var button = buttons[i];
                      if (labels.indexOf(labelOf(button)) === -1) continue;
                      if (button.getAttribute('data-audio-kind') !== kind) {
                        button.setAttribute('data-audio-kind', kind);
                      }
                    }
                  }
                  window.__audioIcon = function (newKind) { try { kind = newKind; apply(); } catch (e) {} };
                  new MutationObserver(function () { try { apply(); } catch (e) {} }).observe(document.documentElement, {
                    subtree: true, childList: true, attributes: true,
                    attributeFilter: ['class', 'aria-label', 'aria-checked'],
                  });
                })();
            """.trimIndent()
        }
    }
}
