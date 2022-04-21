import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.*
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin

val flankExecutable: Configuration by configurations.creating

dependencies { flankExecutable("com.github.flank:flank") { version { prefer("22.04.0") } } }

val simpleFlankExtension = extensions.create<SimpleFlankExtension>("simpleFlank")

plugins.withType(AppPlugin::class.java) {
  tasks.register<FlankVersionTask>("flankVersion") { flankJarClasspath.from(flankExecutable) }

  val appExtension = requireNotNull(extensions.findByType<ApplicationAndroidComponentsExtension>())
  appExtension.onVariants { variant ->
    val debugApkDir: Provider<Directory> = variant.artifacts.get(SingleArtifact.APK)
    val testApkDir: Provider<Directory>? = variant.androidTest?.artifacts?.get(SingleArtifact.APK)

    if (testApkDir != null) {
      val builtArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
      val apkProvider: Provider<File> =
          debugApkDir.map { apk ->
            file { builtArtifactsLoader.load(apk)?.elements?.single()?.outputFile }
          }
      val yamlWriterTask: TaskProvider<FlankYmlWriterTask> =
          registerFlankYamlWriter(
              variant,
              testApkDir,
              builtArtifactsLoader,
              requireNotNull(project.extensions.findByType<ApplicationExtension>()))
      yamlWriterTask.configure { appApk.fileProvider(apkProvider) }
      registerFlankRun(
              variant,
              testApkDir,
              builtArtifactsLoader,
          )
          .configure {
            flankYaml.set(yamlWriterTask.get().flankYaml)
            appApk.fileProvider(apkProvider)
          }
      registerFlankDoctor(variant, testApkDir, builtArtifactsLoader).configure {
        flankYaml.set(yamlWriterTask.get().flankYaml)
        appApk.fileProvider(apkProvider)
      }
    }
  }
  tasks.register("flankRun") { dependsOn(tasks.withType<FlankRunTask>()) }
  verifyNotDefaultKeystore()
}

plugins.withType(LibraryPlugin::class.java) {
  tasks.register<FlankVersionTask>("flankVersion") { flankJarClasspath.from(flankExecutable) }

  useFixedKeystore()
  val libraryExtension = requireNotNull(extensions.findByType<LibraryAndroidComponentsExtension>())
  libraryExtension.onVariants { variant ->
    val testApkDir: Provider<Directory>? = variant.androidTest?.artifacts?.get(SingleArtifact.APK)

    if (testApkDir != null) {
      val copySmallApp: Copy = getSmallAppTask()
      val builtArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
      val yamlWriterTask =
          registerFlankYamlWriter(
              variant,
              testApkDir,
              builtArtifactsLoader,
              requireNotNull(project.extensions.findByType<LibraryExtension>()))
      yamlWriterTask.configure {
        dependsOn(copySmallApp)
        appApk.value { files(copySmallApp).asFileTree.matching { include("*.apk") }.singleFile }
      }
      registerFlankRun(
              variant,
              testApkDir,
              builtArtifactsLoader,
          )
          .configure {
            flankYaml.set(yamlWriterTask.get().flankYaml)
            dependsOn(copySmallApp)
            appApk.value { files(copySmallApp).asFileTree.matching { include("*.apk") }.singleFile }
          }
      registerFlankDoctor(variant, testApkDir, builtArtifactsLoader).configure {
        flankYaml.set(yamlWriterTask.get().flankYaml)
        dependsOn(copySmallApp)
        appApk.value { files(copySmallApp).asFileTree.matching { include("*.apk") }.singleFile }
      }
    }
  }
  tasks.register("flankRun") { dependsOn(tasks.withType<FlankRunTask>()) }
}

fun registerFlankYamlWriter(
    variant: Variant,
    testApkDir: Provider<Directory>,
    builtArtifactsLoader: BuiltArtifactsLoader,
    androidExtension: CommonExtension<*, *, *, *>
): TaskProvider<FlankYmlWriterTask> =
    tasks.register<FlankYmlWriterTask>("flankYaml${variant.name.capitalize()}") {
      projectId.set(simpleFlankExtension.projectId)
      flankProject.set(getFlankProject())
      this@register.variant.set(variant.name)
      useOrchestrator.set(
          provider {
            androidExtension.testOptions.execution.toUpperCase() == "ANDROIDX_TEST_ORCHESTRATOR"
          })

      device.set(NexusLowRes.deviceForMinSdk(variant.minSdkVersion.apiLevel))
      testApk.fileProvider(
          testApkDir.map { apk ->
            file { builtArtifactsLoader.load(apk)?.elements?.single()?.outputFile }
          })
    }

fun registerFlankRun(
    variant: Variant,
    testApkDir: Provider<Directory>,
    builtArtifactsLoader: BuiltArtifactsLoader,
): TaskProvider<FlankRunTask> =
    tasks.register<FlankRunTask>("flankRun${variant.name.capitalize()}") {
      flankJarClasspath.from(flankExecutable)

      serviceAccountCredentials.set(simpleFlankExtension.credentialsFile)
      this@register.variant.set(variant.name)
      hermeticTests.set(simpleFlankExtension.hermeticTests)
      testApk.fileProvider(
          testApkDir.map { apk ->
            file { builtArtifactsLoader.load(apk)?.elements?.single()?.outputFile }
          })
      val dumpShards: String? by project
      this@register.dumpShards.set(dumpShards.toBoolean())
    }

fun registerFlankDoctor(
    variant: Variant,
    testApkDir: Provider<Directory>,
    builtArtifactsLoader: BuiltArtifactsLoader
): TaskProvider<FlankDoctorTask> =
    tasks.register<FlankDoctorTask>("flankDoctor${variant.name.capitalize()}") {
      flankJarClasspath.from(flankExecutable)
      this@register.variant.set(variant.name)
      testApk.fileProvider(
          testApkDir.map { apk ->
            file { builtArtifactsLoader.load(apk)?.elements?.single()?.outputFile }
          })
    }
