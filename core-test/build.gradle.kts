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
  implementation(project(":core"))
  implementation(libs.guava)
}