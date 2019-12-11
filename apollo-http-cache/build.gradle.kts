apply(plugin = "java-library")


withConvention(JavaPluginConvention::class) {
  targetCompatibility = JavaVersion.VERSION_1_7
  sourceCompatibility = JavaVersion.VERSION_1_7
}

dependencies {
  add("compileOnly", groovy.util.Eval.x(project, "x.dep.jetbrainsAnnotations"))

  add("api", groovy.util.Eval.x(project, "x.dep.okHttp.okHttp"))
  add("api", project(":apollo-api"))

  add("testImplementation", groovy.util.Eval.x(project, "x.dep.junit"))
  add("testImplementation", groovy.util.Eval.x(project, "x.dep.truth"))
}

tasks.withType<Javadoc> {
  options.encoding = "UTF-8"
}
