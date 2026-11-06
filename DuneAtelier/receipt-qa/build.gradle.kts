plugins { java }
repositories { mavenCentral() }
val installedArtifact = providers.gradleProperty("installedArtifact").orNull
val mysqlQa = providers.gradleProperty("qaMySql").orNull == "true"
dependencies {
 implementation("com.google.code.gson:gson:2.11.0")
 if (installedArtifact != null) testImplementation(files(installedArtifact))
 testImplementation("com.h2database:h2:2.3.232")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
 if (mysqlQa) testRuntimeOnly("com.mysql:mysql-connector-j:26.10.0")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
sourceSets {
 main {
  if (installedArtifact == null) { java.setSrcDirs(listOf("../src/main/java")); java.include("**/DeliveryStore.java") }
  else java.setSrcDirs(emptyList<String>())
 }
 test {
  java.setSrcDirs(if (mysqlQa) listOf("src/test/java") else listOf("../src/test/java"))
  if (mysqlQa) java.include("**/DeliveryMySqlTest.java", "**/ReceiptCrashProcess.java")
  else java.include("**/DeliveryStoreTest.java", "**/DeliveryCheckpointTest.java")
 }
}
tasks.test {
 useJUnitPlatform();testLogging {events("passed", "failed", "skipped")}
 installedArtifact?.let { systemProperty("installedArtifact",file(it).canonicalPath) }
 doFirst {systemProperty("receiptTestClasspath",classpath.asPath)}
}
