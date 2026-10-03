/*
 * Copyright © 2026 Jean Silva
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *                  https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

@file:Suppress("UnstableApiUsage")

plugins {
  `java-library`
}

group = "com.jeanbarrossilva"
java.toolchain.languageVersion = JavaLanguageVersion.of(27)
repositories.mavenCentral()
version = "1.0.0"

dependencies {
  implementation(libs.guava)
  testImplementation(libs.apache.commons.collections)
  testImplementation(libs.assertJ)
}

testing.suites.named<JvmTestSuite>("test") {
  useJUnit(libs.versions.junit)
}