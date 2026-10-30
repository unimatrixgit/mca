plugins { java }
version = "1.0.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies {
 compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.jar { archiveFileName.set("DuneProtect.jar") }
