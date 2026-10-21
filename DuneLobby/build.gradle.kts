plugins { java }
group = "com.example"
version = "1.1.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.processResources { from("artwork") { into("artwork") } }

repositories { maven("https://repo.opencollab.dev/main/") }
dependencies { compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") }
sourceSets.main { java.srcDir("../gui-common/src/main/java") }

tasks.jar { archiveFileName.set("DuneRealms-Lobby.jar") }
