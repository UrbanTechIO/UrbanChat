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
                })();
            """.trimIndent()
        }
    }
}
