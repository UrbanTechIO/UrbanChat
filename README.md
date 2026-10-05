# UrbanChat

UrbanChat is a private [Matrix](https://matrix.org/) client for Android, built on top of [Element X](https://github.com/element-hq/element-x-android). It uses the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) underneath, targets devices running Android 7+, and is written using [Jetpack Compose](https://developer.android.com/jetpack/compose) with navigation managed by [Appyx](https://github.com/bumble-tech/appyx).

## Table of contents

<!--- TOC -->

* [Acknowledgements](#acknowledgements)
* [What UrbanChat adds to Element X](#what-urbanchat-adds-to-element-x)
* [Screenshots](#screenshots)
* [Rust SDK](#rust-sdk)
* [Minimum SDK version](#minimum-sdk-version)
* [Build instructions](#build-instructions)
* [Copyright and License](#copyright-and-license)

<!--- END -->

## Acknowledgements

UrbanChat is a fork of [Element X Android](https://github.com/element-hq/element-x-android), built and maintained privately by [UrbanTechIO](https://github.com/UrbanTechIO). Enormous credit and thanks go to [Element](https://element.io/) and [New Vector Ltd](https://element.io/) for building and open-sourcing the original client this project is based on — including its architecture, the Compound design system, and its integration with the Matrix Rust SDK. UrbanChat exists to customize that foundation for private, personal use, not to compete with or replace it.

If you're looking for the official, publicly supported Matrix client, please use [Element X](https://github.com/element-hq/element-x-android) directly.

## What UrbanChat adds to Element X

Everything below is in UrbanChat and not in Element X. Features Element X already has are not listed.

### Look and feel

* **Theme colour** that tints the home screen and headers.
* **Frosted glass** chat header, message bar and home header, with separate transparency sliders for the header bar, the composer bar and the message bubbles.
* **Chat background**: a solid colour or your own photo as wallpaper.
* **Custom message colours** for outgoing and incoming bubbles.

### Chat list and home screen

* **Favorites, People and Groups sections**, with a switch to turn the sections off and see one list sorted by latest activity. A **Direct chat** setting in room details lets you choose whether a chat counts as a private 1:1 conversation on your device.
* **Calls tab** with a call log: outgoing, incoming, missed and declined calls and how long answered ones lasted. Tapping an entry opens that chat at the call.
* **Voice message mini player** under the filter chips, with play/pause, progress and a close button.
* **Online status**: a green or grey dot next to people in the chat list and in the chat header, and a switch to hide your own status.

### Privacy

* **Locked chats**: lock any chat to hide it from the list. A locked chat opens only after your device unlock (fingerprint, PIN or pattern), and you can reveal the locked list by typing an access code into the search bar. Sharing into a locked chat also asks for the unlock.
* **Disable link previews** switch.

### Messages and media

* **Search inside a chat.**
* **Sound for open chats**: play a sound for messages that arrive in the chat you are looking at.
* **Download to Gallery**: a download button on received photos and videos that saves them to an UrbanChat folder in your gallery.
* **Image editor** before sending, also when sharing from another app: crop, rotate, flip, draw, arrows, text, pixelate an area, and undo.
* **Large videos always send**: a video is re-encoded to fit the server's upload limit instead of failing, whatever its length.
* **Sharing a video from another app follows your upload settings**, with a quality choice (original, 1080p, 720p, 360p) when optimisation is off and the quality is high.
* **Original Size** switch: send videos untouched. A video over the server limit is still compressed so it can be sent.

### Voice messages

* Hold-to-record starts faster, with smoother animations for the swipe-up lock and for deleting a recording.
* **Send straight from hands-free recording**, with no separate stop step.
* Playback **keeps going when you leave the chat**.

### Calls

* **Call timer** under the caller's name once the call is answered.
* **Full-width audio output picker** (Phone, Speaker, Bluetooth, headset) that applies reliably, and an output button that shows the real output.
* Calls start on the **loudspeaker**, and a **proximity sensor** moves the sound to the earpiece when you hold the phone to your ear and back when you move it away. It never overrides a Bluetooth or wired choice.
* A speaker choice made while the call is still ringing is kept after it is answered.
* **Bluetooth audio on Android 11 and older**, which Element X blocks.
* The call controls no longer flicker, and the microphone keeps working when the screen turns off.

## Screenshots

Here are some screenshots of the application:

<!--
Commands run before taking the screenshots:
adb shell settings put system time_12_24 24
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1337
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100

And to exit demo mode:
adb shell am broadcast -a com.android.systemui.demo -e command exit
-->

|<img src="./docs/images-lfs/screen_1_light.png" width="280" />|<img src="./docs/images-lfs/screen_2_light.png" width="280" />|<img src="./docs/images-lfs/screen_3_light.png" width="280" />|<img src="./docs/images-lfs/screen_4_light.png" width="280" />|
|-|-|-|-|
|<img src="./docs/images-lfs/screen_1_dark.png" width="280" />|<img src="./docs/images-lfs/screen_2_dark.png" width="280" />|<img src="./docs/images-lfs/screen_3_dark.png" width="280" />|<img src="./docs/images-lfs/screen_4_dark.png" width="280" />|

## Rust SDK

UrbanChat leverages the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) through an FFI layer that the client directly imports and uses, the same way Element X does.

## Minimum SDK version

UrbanChat requires a minimum SDK version of 24 (Android 7.0, Nougat).

## Build instructions

Just clone the project and open it in Android Studio. Make sure to select the
`app` configuration when building (as there are also sample apps in the project).

To build against a local copy of the Rust SDK, see the [Developer
onboarding](docs/_developer_onboarding.md#building-the-sdk-locally) instructions.

## Copyright and License

Copyright (c) 2026 UrbanTechIO.

UrbanChat is a derivative work of Element X Android:

Copyright (c) 2025 Element Creations Ltd.
Copyright (c) 2022 - 2025 New Vector Ltd.

This software is dual licensed by Element Creations Ltd (Element). It can be used either:

(1) for free under the terms of the GNU Affero General Public License (as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version); OR

(2) under the terms of a paid-for Element Commercial License agreement between you and Element (the terms of which may vary depending on what you and Element have agreed to).

Unless required by applicable law or agreed to in writing, software distributed under the Licenses is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Licenses for the specific language governing permissions and limitations under the Licenses.
