# TOM-Client-WearOS

An Android smartwatch application to read and send sensor data to the server.
- This application is based on Google’s [ExerciseSampleCompose](https://github.com/android/health-samples/tree/main/health-services/ExerciseSampleCompose) using ExerciseClient API. 
- A Room database has been added to save exercise metrics such as heart rate, calories, speed, etc., in the local database on the watch. 
- It also uses Ktor OkHttp to send exercise data to the server using web sockets.
- See overview [here](https://docs.google.com/document/d/1ZGOYtiGop0cy0YYSsAwoxaNRwmR8lIh288LWmDleArA/edit?usp=sharing)


## Requirements
- AndroidStudio


## Installation
- See [Create and run an app on Wear OS](https://developer.android.com/training/wearables/get-started/creating) for more information on how to create a Wear OS project.
- Tested on Samsung Galaxy Watch 4

### Search Location
- In order to get search location suggestions, we use either [Google Maps Places API](https://developers.google.com/maps/documentation/places/web-service/overview)
  [Nominatim OSM API](https://nominatim.org/release-docs/develop/api/Overview/).
    - To use Google Maps Places API, you need to have a [Google Cloud Platform (GCP)](https://console.cloud.google.com/) account and enable the Places API.
    - To use Nominatim OSM API, you do not need an API key.

### Choosing which API to use

- In `app/src/main/java/com/hci/tom/android/presentation/ExerciseViewModel.kt`, you can modify the
  options to choose which API you would like to use for search locations.

```kotlin
companion object {
    // 0 for OpenStreetMap, 1 for Google Maps
    private const val PLACES_OPTION = 0
}
```

### Setting up config files

- Create an object class named `ApiKeys.kt` in `app/src/main/java/com/hci/tom/android/network`
  directory and add the following code:

```kotlin
package com.hci.tom.android.network

object ApiKeys {
    const val GOOGLE_MAP_API_KEY = "{YOUR_GOOGLE_MAPS_API_KEY}"
}
```

- In `app/src/main/java/com/hci/tom/android/network/Credentials.kt`, modify the code accordingly:

```kotlin
package com.hci.tom.android.network

object Credentials {
    const val SERVER_URL = "ws://{IP_ADDRESS}:{SERVER_PORT}"
}
```
> You can set the IP address to `10.0.2.2` if you are using the emulator. 
This is because the emulator runs behind a virtual router isolated from your computer network interfaces, `10.0.2.2` is an alias to your host loopback interface. (127.0.0.1 on development machine)
You can refer to https://developer.android.com/studio/run/emulator-networking for more info.


##  ADB debug from computer
- [Debugging](https://developer.android.com/training/wearables/get-started/debugging)
- Go to platform-tools `cd <USER_FOLDER>\Android\Sdk\platform-tools` (`<USER_FOLDER>` is the folder where Android SDK is installed, e.e., `C:\Users\<NAME>\AppData\Local\`)
- Connect adb, `adb connect <WATCH_IP>`
- To disconnect, `adb disconnect`


## Application Execution
### Emulator

#### Simulating an exercise session
- To simulate an exercise session on an emulator, you can use the following command in the terminal:
```shell
adb shell am broadcast -a "whs.synthetic.user.START_EXERCISE"--ei exercise_options_heart_rate 90 --ef exercise_options_average_speed 2.5 --ez exercise_options_use_location true com.google.android.wearable.healthservices
```
- You can visit [this link](https://developer.android.com/training/wearables/health-services/synthetic-data) if you would like to modify the simulated run stats.
**Ensure that developer options is enabled on the emulator as well.**

#### Simulating voice input
- To enable microphone input on an emulator, you can use the following command in the terminal:
```shell
adb emu avd hostmicon
```
Make sure to enable this option in extended controls as well, you have to enable it everytime you restart the emulator:
![image](https://github.com/NUS-SSI/TOM-Client-WearOS/assets/95197450/5a195672-d1f2-463b-84b2-5d276155f415)


## Protobuf

- Change to Project View at the top left of Android Studio.

![image](https://github.com/NUS-SSI/TOM-Client-WearOS/assets/95197450/61a68a4d-ceb6-4d49-89bc-7f319b440795)

- Create your proto file in `app/src/main/proto`, create a new `proto` directory if necessary.
  Please refer [here](https://protobuf.dev/getting-started/javatutorial/) for more info on how to
  structure your proto file.

![image](https://github.com/NUS-SSI/TOM-Client-WearOS/assets/95197450/a8f4f4d1-ebb0-403e-830e-0ad900532fee)

- You can set the package directory using `option java_package = "{dir_path}"` and the generated
  class name using `option java_outer_classname = "{class_name}"` before the message and after the
  syntax.
- Build the project and the generated class should be
  in `app/build/generated/source/proto/debug/java/{dir_path}` and you can import the class from
  there to use the class builder functions.

![image](https://github.com/NUS-SSI/TOM-Client-WearOS/assets/95197450/99514812-78f8-4f7a-bc67-9eb388d6f88b)


## Development

### Guides
- [Basic guide, 7 min](https://drive.google.com/file/d/1QhBVzDGhJ_ONVjSbuQv7CivHg0anIEEp/view?usp=drive_link)  

### Linting
**Intellij IDEA**
1. Install the ktlint plugin in the Marketplace [here](https://github.com/nbadal/ktlint-intellij-plugin)

**Command-Line**
1. Install ktlint [Guide](https://pinterest.github.io/ktlint/0.49.1/install/cli/#download-and-verification)
2. Run `ktlint --editorconfig=.editorconfig`


## References
- [ExerciseSampleCompose](https://github.com/android/health-samples/tree/main/health-services/ExerciseSampleCompose)
- [Protobuf Gradle Plugin](https://github.com/google/protobuf-gradle-plugin)
- [Google Maps Places API](https://developers.google.com/maps/documentation/places/web-service/overview)
- [Nominatim OSM API](https://nominatim.org/release-docs/develop/api/Overview/)



