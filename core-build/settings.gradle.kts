rootProject.name = "core-build"

rootProject.projectDir.parentFile
    .listFiles()
    .filter { it.isDirectory }
    .filter { it.name.startsWith("apollo-") }
    .forEach {
      if (System.getProperty("idea.sync.active") != null
          && it.name in listOf("apollo-android-support", "apollo-idling-resource")) {
        return@forEach
      }
      include(it.name)
      project(":${it.name}").projectDir = it
    }

includeBuild("../build-logic")
