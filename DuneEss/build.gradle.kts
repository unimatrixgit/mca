plugins { java }
group = "com.example"
version = "1.0.4"
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("org.postgresql:postgresql:42.7.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") { exclude(group = "org.bukkit", module = "bukkit") }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.jar {
    archiveBaseName.set("DuneEss")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

repositories { maven("https://repo.opencollab.dev/main/") }
dependencies { compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") }
sourceSets.main { java.srcDir("../gui-common/src/main/java") }

tasks.jar { archiveFileName.set("DuneRealms-Essentials.jar") }

// Explicit test instrumentation works on Java 21 without dynamic self-attachment.
val mockitoAgent by configurations.creating
dependencies { mockitoAgent("org.mockito:mockito-core:5.15.2") { isTransitive = false } }
tasks.test { jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}") }
