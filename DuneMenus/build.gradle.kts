plugins { java }
group="com.example"
version="1.2.0"
repositories { mavenCentral();maven("https://repo.papermc.io/repository/maven-public/");maven("https://repo.opencollab.dev/main/");maven("https://jitpack.io") }
dependencies {
 compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
 compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
 compileOnly("org.geysermc.cumulus:cumulus:1.1.2")
 testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
val mockitoAgent by configurations.creating
dependencies {mockitoAgent("org.mockito:mockito-core:5.15.2"){isTransitive=false}}
tasks.test {useJUnitPlatform();jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")}
