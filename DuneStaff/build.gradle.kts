plugins { java }
version="1.0.1"
repositories {mavenCentral();maven("https://repo.papermc.io/repository/maven-public/")}
dependencies {
 compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 compileOnly("com.google.code.gson:gson:2.11.0")
 testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("com.google.code.gson:gson:2.11.0")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testImplementation("com.h2database:h2:2.3.232")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
val mockitoAgent by configurations.creating
dependencies {mockitoAgent("org.mockito:mockito-core:5.15.2"){isTransitive=false}}
tasks.test {useJUnitPlatform();jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")}
tasks.jar {isPreserveFileTimestamps=false;isReproducibleFileOrder=true}

