/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.testing.screenshot.build

import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.TestExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.*
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.facebook.testing.screenshot.generated.ScreenshotTestBuildConfig
import org.gradle.api.*
import java.util.UUID
import org.gradle.api.tasks.TaskProvider
import java.io.File

open class ScreenshotsPluginExtension {
  /** The directory to store recorded screenshots in */
  var recordDir = "screenshots"
  /** Whether to have the plugin dependency automatically add the core dependency */
  var addDeps = true
  /** Whether to store screenshots in device specific folders */
  var multipleDevices = false
  /** The python executable to use */
  var pythonExecutable = "python"
  /** The directory to compare screenshots from in verify only mode */
  var referenceDir: String? = null
  /** The directory to save failed screenshots */
  var failureDir: String? = null
  /** Whether to tar the screenshots in an archive file to transfer */
  var bundleResults = false

  var testRunId: String = UUID.randomUUID().toString()
}

class ScreenshotsPlugin : Plugin<Project> {
  companion object {
    const val GROUP = "Screenshot Test"
    const val DEPENDENCY_GROUP = "com.facebook.testing.screenshot"
    const val DEPENDENCY_CORE = "core"
  }

  private lateinit var screenshotExtensions: ScreenshotsPluginExtension

  override fun apply(project: Project) {
    val extensions = project.extensions
    screenshotExtensions = extensions.create("screenshots", ScreenshotsPluginExtension::class.java)

    project.afterEvaluate {
      if (screenshotExtensions.addDeps) {
        it.dependencies.add(
            getAndroidDependencyConfiguration(project),
            "$DEPENDENCY_GROUP:$DEPENDENCY_CORE:${ScreenshotTestBuildConfig.VERSION}")
      }
    }

    setupAndroidExtension(project)
  }

  private fun getAndroidDependencyConfiguration(project: Project): String {
    return if (isTestExtension(project)) {
      "implementation"
    } else {
      "androidTestImplementation"
    }
  }

  private fun setupAndroidExtension(project: Project) {
    val variants: DomainObjectSet<out ApkVariant>
    val config: DefaultConfig
    if (isTestExtension(project)) {
      val ext = getTestExtension(project)
      variants = ext.applicationVariants
      config = ext.defaultConfig
    } else {
      val ext = getProjectExtension(project)
      variants = ext.testVariants
      config = ext.defaultConfig
    }
    variants.all { variant ->
      generateTaskForVariant(project, variant)
    }

    config.testInstrumentationRunnerArguments["SCREENSHOT_TESTS_RUN_ID"] =
        screenshotExtensions.testRunId
  }

  private fun isTestExtension(project: Project): Boolean {
    return project.extensions.findByType(TestExtension::class.java) != null
  }

  private fun getTestExtension(project: Project): com.android.build.gradle.TestExtension {
    val extensions = project.extensions
    val plugins = project.plugins
    return if (plugins.hasPlugin("com.android.test")) {
      extensions.getByType(com.android.build.gradle.TestExtension::class.java)
    } else {
      throw IllegalArgumentException("Project is not a test module")
    }
  }

  private fun generateTaskForBuildType(project: Project, variant: ApplicationVariant) {
    val nameProvider: VariantNameProvider = { variant.name }
    val directoryProvider: ApkOutputDirectoryProvider = {
      val packageTask = variant.packageApplicationProvider.orNull
          ?: throw IllegalArgumentException("Can't find package application provider")

      packageTask.outputDirectory.asFile.get()
    }
    val apkFilenameProvider: ApkFilenameProvider = {
      val output = variant.outputs.find { it is ApkVariantOutput } as? ApkVariantOutput
          ?: throw IllegalArgumentException("Can't find APK output")
      output.outputFileName
    }

    generateTasksFor(
        project,
        outputs = variant.outputs,
        variantNameProvider = nameProvider,
        apkOutputDirectoryProvider = directoryProvider,
        apkFilenameProvider = apkFilenameProvider,
        extensionProvider = { screenshotExtensions },
        instrumentationTaskProvider = {
          val instrumentationTask = "connected${nameProvider().capitalize()}AndroidTest"
          project.tasks.named(instrumentationTask)
        }
    )
  }

  private fun generateTaskForVariant(project: Project, variant: ApkVariant) {
    val nameProvider: VariantNameProvider = { variant.name }
    val instrumentationTaskProvider: InstrumentationTaskProvider = {
      val instrumentationTask = if (variant is TestVariant) {
        "connected${nameProvider().capitalize()}"
      } else {
        "connected${nameProvider().capitalize()}AndroidTest"
      }
      project.tasks.named(instrumentationTask)
    }
    val directoryProvider: ApkOutputDirectoryProvider = {
      val packageTask =
          variant.packageApplicationProvider.orNull
              ?: throw IllegalArgumentException("Can't find package application provider")

      packageTask.outputDirectory.asFile.get()
    }
    val apkFilenameProvider: ApkFilenameProvider = {
      val output = variant.outputs.find { it is ApkVariantOutput } as? ApkVariantOutput
          ?: throw IllegalArgumentException("Can't find APK output")
      output.outputFileName
    }

    generateTasksFor(
        project,
        outputs = variant.outputs,
        variantNameProvider = nameProvider,
        apkOutputDirectoryProvider = directoryProvider,
        apkFilenameProvider = apkFilenameProvider,
        extensionProvider = { screenshotExtensions },
        instrumentationTaskProvider = instrumentationTaskProvider
    )
  }

  private fun getProjectExtension(project: Project): TestedExtension {
    val extensions = project.extensions
    val plugins = project.plugins
    return when {
      plugins.hasPlugin("com.android.application") ->
        extensions.findByType(AppExtension::class.java)!!
      plugins.hasPlugin("com.android.library") ->
        extensions.findByType(LibraryExtension::class.java)!!
      else -> throw IllegalArgumentException("Screenshot Test plugin requires Android's plugin")
    }
  }

  private fun <T : ScreenshotTask> registerTask(
      project: Project,
      name: String,
      variantNameProvider: VariantNameProvider,
      apkOutputDirectoryProvider: ApkOutputDirectoryProvider,
      apkFilenameProvider: ApkFilenameProvider,
      extensionProvider: ExtensionProvider,
      instrumentationTaskProvider: InstrumentationTaskProvider,
      clazz: Class<T>
  ): TaskProvider<T> {
    return project.tasks.register(name, clazz).apply {
      configure {
        it.init(
            variantNameProvider,
            apkOutputDirectoryProvider,
            apkFilenameProvider,
            extensionProvider,
            instrumentationTaskProvider,
        )
      }
    }
  }

  private fun generateTasksFor(
      project: Project,
      outputs: DomainObjectCollection<BaseVariantOutput>,
      variantNameProvider: VariantNameProvider,
      apkOutputDirectoryProvider: ApkOutputDirectoryProvider,
      apkFilenameProvider: ApkFilenameProvider,
      extensionProvider: ExtensionProvider,
      instrumentationTaskProvider: InstrumentationTaskProvider,
  ) {

    outputs.all {
      if (it is ApkVariantOutput) {
        val cleanScreenshots =
            registerTask(
                project,
                CleanScreenshotsTask.taskName(variantNameProvider),
                variantNameProvider,
                apkOutputDirectoryProvider,
                apkFilenameProvider,
                extensionProvider,
                instrumentationTaskProvider,
                CleanScreenshotsTask::class.java)
        registerTask(
            project,
            PullScreenshotsTask.taskName(variantNameProvider),
            variantNameProvider,
            apkOutputDirectoryProvider,
            apkFilenameProvider,
            extensionProvider,
            instrumentationTaskProvider,
            PullScreenshotsTask::class.java)
            .dependsOn(cleanScreenshots)

        registerTask(
            project,
            RunScreenshotTestTask.taskName(variantNameProvider),
            variantNameProvider,
            apkOutputDirectoryProvider,
            apkFilenameProvider,
            extensionProvider,
            instrumentationTaskProvider,
            RunScreenshotTestTask::class.java)

        registerTask(
            project,
            RecordScreenshotTestTask.taskName(variantNameProvider),
            variantNameProvider,
            apkOutputDirectoryProvider,
            apkFilenameProvider,
            extensionProvider,
            instrumentationTaskProvider,
            RecordScreenshotTestTask::class.java)

        registerTask(
            project,
            VerifyScreenshotTestTask.taskName(variantNameProvider),
            variantNameProvider,
            apkOutputDirectoryProvider,
            apkFilenameProvider,
            extensionProvider,
            instrumentationTaskProvider,
            VerifyScreenshotTestTask::class.java)
      }
    }
  }
}

typealias ApkOutputDirectoryProvider = () -> File
typealias ApkFilenameProvider = () -> String
typealias ExtensionProvider = () -> ScreenshotsPluginExtension
typealias VariantNameProvider = () -> String
typealias InstrumentationTaskProvider = () -> TaskProvider<Task>