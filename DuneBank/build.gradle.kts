plugins { java }
version="1.0.3"
repositories {mavenCentral();maven("https://repo.papermc.io/repository/maven-public/")}
dependencies {
 compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 compileOnly(files("../../output/gui-performance-20261029/release/DuneCore-1.0.21-gui-performance.jar"))
 testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation(files("../../output/gui-performance-20261029/release/DuneCore-1.0.21-gui-performance.jar"))
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("com.h2database:h2:2.3.232")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
tasks.test {useJUnitPlatform()}
tasks.jar { isPreserveFileTimestamps=false; isReproducibleFileOrder=true }
