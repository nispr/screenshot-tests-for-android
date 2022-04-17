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

open class RunScreenshotTestTask : PullScreenshotsTask() {
  companion object {
    fun taskName(variant: VariantNameProvider) = "run${variant().capitalize()}ScreenshotTest"
  }

  init {
    description = "Installs and runs screenshot tests, then generates a report"
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

    if (verify && extensionProvider().referenceDir != null) {
      return
    }

    dependsOn(instrumentationTaskProvider())
    mustRunAfter(instrumentationTaskProvider())
  }
}
