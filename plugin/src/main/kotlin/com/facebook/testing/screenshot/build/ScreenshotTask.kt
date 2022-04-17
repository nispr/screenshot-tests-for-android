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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input

open class ScreenshotTask : DefaultTask() {

  @Input
  protected lateinit var apkFilenameProvider: ApkFilenameProvider

  @Input
  protected lateinit var apkOutputDirectoryProvider: ApkOutputDirectoryProvider

  @Input
  protected lateinit var variantNameProvider: VariantNameProvider

  @Input
  protected lateinit var extensionProvider: ExtensionProvider

  @Input
  protected lateinit var instrumentationTaskProvider: InstrumentationTaskProvider

  open fun init(
      variantNameProvider: VariantNameProvider,
      apkOutputDirectoryProvider: ApkOutputDirectoryProvider,
      apkFilenameProvider: ApkFilenameProvider,
      extensionProvider: ExtensionProvider,
      instrumentationTaskProvider: InstrumentationTaskProvider
  ) {
    this.variantNameProvider = variantNameProvider
    this.apkOutputDirectoryProvider = apkOutputDirectoryProvider
    this.apkFilenameProvider = apkFilenameProvider
    this.extensionProvider = extensionProvider
    this.instrumentationTaskProvider = instrumentationTaskProvider
  }
}
