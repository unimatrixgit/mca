plugins { java }
group="com.example"
version="1.0.5"
repositories { mavenCentral();maven("https://repo.papermc.io/repository/maven-public/") }
dependencies {
 compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("com.h2database:h2:2.3.232")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
val mockitoAgent by configurations.creating
dependencies {mockitoAgent("org.mockito:mockito-core:5.15.2"){isTransitive=false}}
tasks.test {useJUnitPlatform();jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")}

tasks.withType<Jar> { isPreserveFileTimestamps=false; isReproducibleFileOrder=true }
