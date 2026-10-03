plugins {
  `java-library`
}

java {
  val version = libs.versions
                    .java
                    .get()
                    .replaceFirst('.', '_')
                    .let { JavaVersion.valueOf("VERSION_$it") }
  sourceCompatibility = version
  targetCompatibility = version
}

dependencies {
  api(libs.agrona)
  implementation(libs.guava)
  testImplementation(libs.apache.commons.collections)
  testImplementation(libs.assertJ)
  testImplementation(libs.jUnit4)
}