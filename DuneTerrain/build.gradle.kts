plugins { java }

group = "com.example"
version = "1.0.9"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.jar { archiveBaseName.set("DuneTerrain") }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

tasks.jar { archiveFileName.set("DuneRealms-Terrain.jar") }

val highlandsDatapack by tasks.registering(Zip::class) {
    from("highlands-datapack")
    archiveFileName.set("DuneRealms-Dune-Highlands-Datapack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
tasks.assemble { dependsOn(highlandsDatapack) }

val mockitoAgent by configurations.creating
dependencies { mockitoAgent("org.mockito:mockito-core:5.15.2") { isTransitive = false } }
tasks.test { jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}") }
