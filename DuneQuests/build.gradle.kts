plugins { java }
group="com.dunerealms";version="1.0.1"
repositories {mavenCentral();maven("https://repo.papermc.io/repository/maven-public/")}
dependencies {
 compileOnly(files("../../output/worm-quest-20261029/release/DuneCore-1.0.20-worms.jar"))
 compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 implementation("com.mysql:mysql-connector-j:26.10.0")
 testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4");testImplementation("org.mockito:mockito-core:5.15.2");testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java{toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
tasks.test{useJUnitPlatform()}
tasks.jar{archiveFileName.set("DuneQuests-1.0.1.jar");duplicatesStrategy=DuplicatesStrategy.EXCLUDE;from(configurations.runtimeClasspath.get().map{if(it.isDirectory)it else zipTree(it)});exclude("META-INF/*.SF","META-INF/*.RSA","META-INF/*.DSA")}
