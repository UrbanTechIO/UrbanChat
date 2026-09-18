# UrbanChat

UrbanChat is a private [Matrix](https://matrix.org/) client for Android, built on top of [Element X](https://github.com/element-hq/element-x-android). It uses the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) underneath, targets devices running Android 7+, and is written using [Jetpack Compose](https://developer.android.com/jetpack/compose) with navigation managed by [Appyx](https://github.com/bumble-tech/appyx).

## Table of contents

<!--- TOC -->

* [Acknowledgements](#acknowledgements)
* [Screenshots](#screenshots)
* [Rust SDK](#rust-sdk)
* [Minimum SDK version](#minimum-sdk-version)
* [Build instructions](#build-instructions)
* [Copyright and License](#copyright-and-license)

<!--- END -->

## Acknowledgements

UrbanChat is a fork of [Element X Android](https://github.com/element-hq/element-x-android), built and maintained privately by [UrbanTechIO](https://github.com/UrbanTechIO). Enormous credit and thanks go to [Element](https://element.io/) and [New Vector Ltd](https://element.io/) for building and open-sourcing the original client this project is based on — including its architecture, the Compound design system, and its integration with the Matrix Rust SDK. UrbanChat exists to customize that foundation for private, personal use, not to compete with or replace it.

If you're looking for the official, publicly supported Matrix client, please use [Element X](https://github.com/element-hq/element-x-android) directly.

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
