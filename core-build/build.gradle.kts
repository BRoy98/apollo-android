import okhttp3.Credentials.basic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
  project.apply {
    from(rootProject.file("../gradle/dependencies.gradle"))
  }
  dependencies {
    classpath("com.apollographql.apollo:build-logic")
  }
}

apply(plugin = "com.github.ben-manes.versions")

ApiCompatibility.configure(rootProject)

subprojects {
  apply {
    from(rootProject.file("../gradle/dependencies.gradle"))
  }

  plugins.withType(com.android.build.gradle.BasePlugin::class.java) {
    extensions.configure(com.android.build.gradle.BaseExtension::class.java) {
      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
      }
    }
  }

  plugins.withType(org.gradle.api.plugins.JavaPlugin::class.java) {
    extensions.configure(JavaPluginExtension::class.java) {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    }
  }

  afterEvaluate {
    tasks.withType<KotlinCompile> {
      kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
      }
    }
    (project.extensions.findByName("kotlin")
        as? org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension)?.run {
      sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
      }
    }
  }

  tasks.withType<Test> {
    systemProperty("updateTestFixtures", System.getProperty("updateTestFixtures"))
    systemProperty("codegenTests", System.getProperty("codegenTests"))
    systemProperty("testFilter", System.getProperty("testFilter"))
    testLogging {
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  this.apply(plugin = "signing")

  repositories {
    google()
    mavenCentral()
    jcenter {
      content {
        includeGroup("org.jetbrains.trove4j")
      }
    }
  }

  group = property("GROUP")!!
  version = property("VERSION_NAME")!!

  this.apply(plugin = "maven-publish")
  afterEvaluate {
    configurePublishing()
  }
}

fun Project.configurePublishing() {
  /**
   * Javadoc
   */
  val emptyJavadocJarTaskProvider = tasks.register("javadocJar", org.gradle.jvm.tasks.Jar::class.java) {
    archiveClassifier.set("javadoc")
  }

  /**
   * Sources
   */
  val emptySourcesJarTaskProvider = tasks.register("sourcesJar", org.gradle.jvm.tasks.Jar::class.java) {
    archiveClassifier.set("sources")
  }

  tasks.withType(Jar::class.java) {
      manifest {
        attributes["Built-By"] = findProperty("POM_DEVELOPER_ID") as String?
        attributes["Build-Jdk"] = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.vm.version")})"
        attributes["Created-By"] = "Gradle ${gradle.gradleVersion}"
        attributes["Implementation-Title"] = findProperty("POM_NAME") as String?
        attributes["Implementation-Version"] = findProperty("VERSION_NAME") as String?
      }
  }

  configure<PublishingExtension> {
    publications {
      when {
        plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {
          withType<MavenPublication> {
            // multiplatform doesn't add javadoc by default so add it here
            artifact(emptyJavadocJarTaskProvider.get())
          }
        }
        plugins.hasPlugin("java-gradle-plugin") -> {
          // java-gradle-plugin doesn't add javadoc/sources by default so add it here
          withType<MavenPublication> {
            artifact(emptyJavadocJarTaskProvider.get())
            artifact(emptySourcesJarTaskProvider.get())
          }
        }
        else -> {
          create<MavenPublication>("default") {
            afterEvaluate {// required for android...
              from(components.findByName("java") ?: components.findByName("release"))
            }

            artifact(emptyJavadocJarTaskProvider.get())
            artifact(emptySourcesJarTaskProvider.get())

            pom {
              artifactId = findProperty("POM_ARTIFACT_ID") as String?
            }
          }
        }
      }

      withType<MavenPublication> {
        setDefaultPomFields(this)
      }
    }

    repositories {
      maven {
        name = "pluginTest"
        url = uri("file://${rootProject.buildDir}/localMaven")
      }

      maven {
        name = "ossSnapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        credentials {
          username = System.getenv("SONATYPE_NEXUS_USERNAME")
          password = System.getenv("SONATYPE_NEXUS_PASSWORD")
        }
      }

      maven {
        name = "ossStaging"
        url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
        credentials {
          username = System.getenv("SONATYPE_NEXUS_USERNAME")
          password = System.getenv("SONATYPE_NEXUS_PASSWORD")
        }
      }
    }
  }

  configure<SigningExtension> {
    // GPG_PRIVATE_KEY should contain the armoured private key that starts with -----BEGIN PGP PRIVATE KEY BLOCK-----
    // It can be obtained with gpg --armour --export-secret-keys KEY_ID
    useInMemoryPgpKeys(System.getenv("GPG_PRIVATE_KEY"), System.getenv("GPG_PRIVATE_KEY_PASSWORD"))
    val publicationsContainer = (extensions.get("publishing") as PublishingExtension).publications
    sign(publicationsContainer)
  }
  tasks.withType<Sign> {
    isEnabled = !System.getenv("GPG_PRIVATE_KEY").isNullOrBlank()
  }
}

/**
 * Set fields which are common to all project, either KMP or non-KMP
 */
fun Project.setDefaultPomFields(mavenPublication: MavenPublication) {
  mavenPublication.groupId = findProperty("GROUP") as String?
  mavenPublication.version = findProperty("VERSION_NAME") as String?

  mavenPublication.pom {
    name.set(findProperty("POM_NAME") as String?)
    (findProperty("POM_PACKAGING") as String?)?.let {
      // Do not overwrite packaging if set by the multiplatform plugin
      packaging = it
    }

    description.set(findProperty("POM_DESCRIPTION") as String?)
    url.set(findProperty("POM_URL") as String?)

    scm {
      url.set(findProperty("POM_SCM_URL") as String?)
      connection.set(findProperty("POM_SCM_CONNECTION") as String?)
      developerConnection.set(findProperty("POM_SCM_DEV_CONNECTION") as String?)
    }

    licenses {
      license {
        name.set(findProperty("POM_LICENCE_NAME") as String?)
        url.set(findProperty("POM_LICENCE_URL") as String?)
      }
    }

    developers {
      developer {
        id.set(findProperty("POM_DEVELOPER_ID") as String?)
        name.set(findProperty("POM_DEVELOPER_NAME") as String?)
      }
    }
  }
}

fun subprojectTasks(name: String): List<Task> {
  return subprojects.flatMap { subproject ->
    subproject.tasks.matching { it.name == name }
  }
}

fun isTag(): Boolean {
  val ref = System.getenv("GITHUB_REF")

  return ref?.startsWith("refs/tags/") == true
}

fun shouldPublishSnapshots(): Boolean {
  val eventName = System.getenv("GITHUB_EVENT_NAME")
  val ref = System.getenv("GITHUB_REF")

  return eventName == "push" && (ref == "refs/heads/main" || ref == "refs/heads/dev-3.x")
}

tasks.register("publishSnapshotsIfNeeded") {
  if (shouldPublishSnapshots()) {
    project.logger.log(LogLevel.LIFECYCLE, "Deploying snapshot to OSS Snapshots...")
    dependsOn(subprojectTasks("publishAllPublicationsToOssSnapshotsRepository"))
  }
}

tasks.register("publishToOssStagingIfNeeded") {
  if (isTag()) {
    project.logger.log(LogLevel.LIFECYCLE, "Deploying release to OSS staging...")
    dependsOn(subprojectTasks("publishAllPublicationsToOssStagingRepository"))
  }
}

tasks.register("publishToGradlePortalIfNeeded") {
  if (isTag()) {
    project.logger.log(LogLevel.LIFECYCLE, "Deploying release to Gradle Portal...")
    dependsOn(":apollo-gradle-plugin:publishPlugins")
  }
}

tasks.register("sonatypeCloseAndReleaseRepository") {
  doLast {
    com.vanniktech.maven.publish.nexus.Nexus(
        username = System.getenv("SONATYPE_NEXUS_USERNAME"),
        password = System.getenv("SONATYPE_NEXUS_PASSWORD"),
        baseUrl = "https://oss.sonatype.org/service/local/",
        groupId = "com.apollographql"
    ).closeAndReleaseRepository()
  }
}

tasks.register("fullCheck") {
  subprojects {
    tasks.all {
      if (this.name == "build") {
        this@register.dependsOn(this)
      }
    }
  }
}

/**
 * A task to do (relatively) fast checks when iterating
 */
tasks.register("quickCheck") {
  subprojects {
    tasks.all {
      if (this@subprojects.name in listOf("apollo-compiler", "apollo-gradle-plugin")) {
        if (this.name == "jar") {
          // build the jar but do not test
          this@register.dependsOn(this)
        }
      } else {
        if (this.name == "test" || this.name == "jvmTest") {
          this@register.dependsOn(this)
        }
      }
    }
  }
}
