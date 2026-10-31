# Telly

An Android remote for Android TV and Google TV devices: set-top boxes like the **Airtel Xstream Box** and smart TVs like **BPL's Android / Google TV models**. Telly uses the same Wi-Fi protocol as Google's own TV remote app. The TV doesn't need developer mode, and nothing goes through the internet.

## Features

- **Full remote:** power, D-pad (hold arrows to repeat, hold OK for a long press), swipe touchpad, back/home/menu, volume and channel rockers, media keys, mute, input/source, and a TV settings key.
- **Channels with logos:** add channels with their number and tap a tile to switch the box to it. Telly opens live TV first, then types the number. Logos are fetched from Wikipedia, picked from your gallery, or loaded from an image URL. Includes search, groups, and a popular-channels quick-add.
- **One-tap apps with real icons:** Netflix, YouTube, Prime Video, JioHotstar and Spotify are set up out of the box, with their real icons fetched from Google Play. Any app open on the TV can be saved as a tile with one tap.
- **Shortcuts:** multi-step macros that combine key presses, app launches, deep links, typed text, waits, and channel changes. Examples: open YouTube and search for something, or jump to a specific Netflix title.
- **Type on the TV** from your phone's keyboard.
- **Phone volume buttons** control the TV while the app is open.
- **Live status:** connected or standby, the app currently on screen, and the volume level.
- **Several devices:** switch between your box and TV, and send channel tiles to a specific one.
- **Wake-on-LAN:** used by the power button when the TV is unreachable. The MAC address is read from the TV's certificate during pairing.

## Install

Download `telly-vX.Y.Z.apk` from [Releases](https://github.com/NikhilBhatt-15/telly/releases) and open it on your phone. You may need to allow installing from your browser or file manager. Requires Android 8.0 or later.

Then:

1. Make sure the phone and the TV or box are on the same Wi-Fi.
2. Open Telly, go to **Devices**, then **Add a device**. The TV shows up automatically; if it doesn't, type its IP address.
3. Enter the 6-character code the TV shows.

### Setting up channel switching on the Airtel box

Telly needs the live TV app to be on screen before it types a channel number. By default it presses the Live TV key first. To make tuning more reliable:

1. Open live TV on the box with the normal remote.
2. In Telly, go to **Channels**, then the tune icon, then **Use app on screen**.

From then on, Telly opens that app directly, and skips the step entirely when live TV is already showing. You can also adjust the waiting time and whether to press OK after the number.

## Which TVs work

Any device running **Android TV or Google TV** with the "Android TV Remote Service", which is preinstalled on certified devices: Airtel Xstream boxes, BPL Android/Google TV models (such as the 32H-D4301), Chromecast with Google TV, Mi Box, Sony and TCL Google TVs, and others.

It does **not** work with BPL's cheaper Linux-based TVs or webOS models. Those use different protocols or IR only.

Turning a TV **on** works when it keeps its network on in standby (often a "Quick start" or "Network standby" setting). Otherwise, Telly sends a Wake-on-LAN packet, and HDMI-CEC from a box can wake the TV.

## How it works

Android TV devices run a remote service with two TLS ports:

| Port | Purpose |
|------|---------|
| 6467 | **Pairing ("polo").** The app presents a self-signed client certificate, the TV shows a 6-hex-digit code, and the app proves it saw the code by sending `SHA-256(client modulus, client exponent, server modulus, server exponent, last 4 code digits)`. The first byte of that hash equals the first two code digits, so typos are caught before sending. |
| 6466 | **Remote.** Length-prefixed protobuf messages: key injection (with short/long-press direction), app-link launches (`https://…` deep links, or `market://launch?id=<package>` for any installed app), IME text, plus the TV's power state, volume, and foreground app. The TV pings every 5 seconds, and the app answers. |

The code is plain Kotlin and Jetpack Compose, with no protobuf or crypto libraries. A small hand-written protobuf codec and DER encoder ([`atv/`](app/src/main/java/com/nikhilbhatt/telly/atv)) keep the APK under 2 MB.

```
app/src/main/java/com/nikhilbhatt/telly/
├── atv/          protocol: pairing, remote session, protobuf codec, client certificate, key codes
├── data/         models, JSON store, presets, logo finder
├── ui/           Compose screens: Remote, Channels, Apps, Devices
├── Controller.kt keys, app launches, channel tuning, macros
└── Discovery.kt  mDNS discovery of _androidtvremote2._tcp
```

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35).

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # protocol tests
./gradlew assembleRelease        # signed if keystore.properties exists, unsigned otherwise
```

For signed release builds, create `keystore.properties` in the project root. It is ignored by git.

```properties
storeFile=release.jks
storePassword=…
keyAlias=telly
keyPassword=…
```

## Testing without a TV

- **Unit tests:** [`ProtocolTest`](app/src/test/java/com/nikhilbhatt/telly/atv/ProtocolTest.kt) checks the codec byte for byte against vectors produced with the reference implementation's own protobuf schemas and pairing math ([`tools/vectors.py`](tools/vectors.py)). It also runs a loopback TLS server that demands a client certificate, the way a TV does.
- **Fake TV:** [`tools/fake_tv.py`](tools/fake_tv.py) is a fake Android TV that runs both ports, shows a pairing code, and logs every key, link, and text it receives. Run it on your PC, then add `10.0.2.2` in the emulator, or your PC's IP from a phone.

  ```sh
  pip install androidtvremote2 pyopenssl
  python tools/fake_tv.py
  ```

## Credits

The protocol was reverse-engineered by the community. This app builds on the documentation and reference code in [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2), [louis49/androidtv-remote](https://github.com/louis49/androidtv-remote), and Google's [google-tv-pairing-protocol](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/).

App icons and channel logos are trademarks of their owners. Telly downloads them at runtime from Google Play and Wikipedia and does not ship them.
