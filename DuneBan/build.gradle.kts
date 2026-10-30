plugins { base }
allprojects { group="com.dunerealms";version="1.0.0";repositories{mavenCentral();maven("https://repo.papermc.io/repository/maven-public/")} }
subprojects {
 apply(plugin="java")
 extensions.configure<JavaPluginExtension>{toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
 tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
 tasks.withType<Test>{useJUnitPlatform()}
}
