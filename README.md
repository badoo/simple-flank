# simple-flank
simple-flank is a new gradle plugin with a clear focus: make the setup as simple as possible.

Applied to any application or library module it creates a task called `runFlank` that will run all the android tests (all flavors, debug buildType by default)

Tests will be executed using NexusLowRes emulators, and the lower sdk possible (between minSdk and available SDKs at Firebase)

# Setup

Apply the plugin on the application/library modules you want to run tests:
```
plugins {
  ...
  id("io.github.flank.gradle.simple-flank")
}
```

add your firebase credentials to the rootproject as `ftl-credentials.json`

That's it, run `./gradlew runFlank` and get the results.

# Optional configuration

## Changing the credentials file

```
simpleFlank {
  credentialsFile.set(project.rootProject.file("some-credentials.json"))
}
```

## Making the tests cacheable

If you are using the Gradle BuildCache, you just need to declare your tests as hermetic ( isolated and reproducible )

```
simpleFlank {
  hermeticTests.set(true)
}
```

or, even better, if all your modules have hermetic tests you can add to the `gradle.properties`:
`simple-flank.hermeticTests=true`

## Choosing the devices manually

```
simpleFlank {
  devices.set(listOf(
    io.github.flank.gradle.NexusLowRes(23),
    io.github.flank.gradle.NexusLowRes(30, "es_ES", io.github.flank.gradle.Device.Orientation.landscape),
    io.github.flank.gradle.Device("oriole",31,"Google","Pixel 6")
  ))
}
```

## changing Flank version

```
dependencies {
  flankExecutable("com.github.flank:flank:21.11.0")
}
```

## Orchestrator

`simple-flank` will configure it automatically if you are using "ANDROIDX_TEST_ORCHESTRATOR"


# FAQ

## Can I change the buildType to run?

Android already provides a way for you to do it:
```
android {
  ...
  testBuildType = "beta"
  ...
}
```

## How to debug shards?

`./gradlew runFlank -PdumpShards=true`

## What if I want to use a different flank config?

Feel free to [create your own task](docs/manual_tasks_creation.md) and provide the config yaml you want.
