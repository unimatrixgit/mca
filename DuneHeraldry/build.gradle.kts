plugins { java }
group = "com.dunerealms"
version = "1.0.1"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.release.set(21) }
tasks.jar { archiveFileName.set("DuneHeraldry-1.0.1.jar"); isPreserveFileTimestamps=false; isReproducibleFileOrder=true }
