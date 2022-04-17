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

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File


open class PullScreenshotsTask : ScreenshotTask() {
  companion object {
    fun taskName(variantName: VariantNameProvider) = "pull${variantName().capitalize()}Screenshots"

    fun getReportDir(project: Project, variantName: VariantNameProvider): File =
        File(project.buildDir, "screenshots" + variantName().capitalize())
  }

  private lateinit var apkPath: File

  @Input
  protected var verify = false

  @Input
  protected var record = false

  @Input
  protected var bundleResults = false

  @Input
  protected lateinit var testRunId: String

  init {
    description = "Pull screenshots from your device"
    group = ScreenshotsPlugin.GROUP
  }

  override fun init(
      variantNameProvider: VariantNameProvider,
      apkOutputDirectoryProvider: ApkOutputDirectoryProvider,
      apkFilenameProvider: ApkFilenameProvider,
      extensionProvider: ExtensionProvider,
      instrumentationTaskProvider: InstrumentationTaskProvider,
  ) {
    super.init(
        variantNameProvider,
        apkOutputDirectoryProvider,
        apkFilenameProvider,
        extensionProvider,
        instrumentationTaskProvider,
    )

    apkPath = File(apkOutputDirectoryProvider(), apkFilenameProvider())
    bundleResults = extensionProvider().bundleResults
    testRunId = extensionProvider().testRunId
  }

  @TaskAction
  fun pullScreenshots() {
    val codeSource = ScreenshotsPlugin::class.java.protectionDomain.codeSource
    val jarFile = File(codeSource.location.toURI().path)
    val isVerifyOnly = verify && extensionProvider().referenceDir != null

    val outputDir =
        if (isVerifyOnly) {
          File(extensionProvider().referenceDir)
        } else {
          getReportDir(project, variantNameProvider)
        }

    assert(if (isVerifyOnly) outputDir.exists() else !outputDir.exists())

    project.exec {
      it.executable = extensionProvider().pythonExecutable
      it.environment("PYTHONPATH", jarFile)

      it.args =
          mutableListOf(
              "-m",
              "android_screenshot_tests.pull_screenshots",
              "--apk",
              apkPath.absolutePath,
              "--test-run-id",
              testRunId,
              "--temp-dir",
              outputDir.absolutePath)
              .apply {
                if (verify) {
                  add("--verify")
                } else if (record) {
                  add("--record")
                }

                if (verify || record) {
                  add(extensionProvider().recordDir)
                }

                if (verify && extensionProvider().failureDir != null) {
                  add("--failure-dir")
                  add("${extensionProvider().failureDir}")
                }

                if (extensionProvider().multipleDevices) {
                  add("--multiple-devices")
                  add("${extensionProvider().multipleDevices}")
                }

                if (isVerifyOnly) {
                  add("--no-pull")
                }

                if (bundleResults) {
                  add("--bundle-results")
                }
              }

      println(it.args)
    }
  }
}
