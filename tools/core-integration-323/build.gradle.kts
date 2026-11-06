plugins { java }
repositories {mavenCentral();maven("https://repo.papermc.io/repository/maven-public/");maven("https://jitpack.io")}
dependencies {
 implementation("org.ow2.asm:asm-tree:9.7.1")
 compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
 testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testRuntimeOnly("com.github.MilkBowl:VaultAPI:1.7"){exclude(group="org.bukkit",module="bukkit")}
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {toolchain.languageVersion.set(JavaLanguageVersion.of(21))}
tasks.withType<JavaCompile>{options.encoding="UTF-8";options.release.set(21)}
val generated=layout.buildDirectory.dir("generated/donor")
val core=layout.projectDirectory.file("../../DuneRealms-minecraft-only/src/main/java/com/example/dunerealms/DuneCore.java")
val bridge=layout.projectDirectory.file("../../DuneRealms-minecraft-only/src/main/java/com/example/dunerealms/StaffMenuBridge.java")
val generateDonor by tasks.registering {
 inputs.files(core,bridge);outputs.dir(generated)
 doLast {
  val source=core.asFile.readText().replace("\r\n","\n")
  val methods=Regex("(?s)    public void adminAssignFaction\\(.*?\n    }").findAll(source).toList()
  check(methods.size==1){"Expected exactly one current public facade"}
  val dir=generated.get().asFile.resolve("com/example/dunerealms");dir.mkdirs()
  dir.resolve("DuneCore.java").writeText("""
package com.example.dunerealms;
import java.util.*;import org.bukkit.Bukkit;import org.bukkit.entity.Player;
/** Compilation donor only: constructor and checkpoint stub must never enter a candidate. */
public final class DuneCore {
 private final Map<UUID,String> selected=new HashMap<>();
 public void transactNetworkInventory(Player target,java.util.function.Consumer<java.sql.Connection> operation)throws Exception{throw new UnsupportedOperationException("Donor checkpoint stub must never be merged");}
${methods.single().value}
}
"""
  )
  dir.resolve("StaffMenuBridge.java").writeText(bridge.asFile.readText())
 }
}
sourceSets.main {java.srcDir(generated)}
tasks.compileJava {dependsOn(generateDonor)}
val mockitoAgent by configurations.creating
dependencies {mockitoAgent("org.mockito:mockito-core:5.15.2"){isTransitive=false}}
tasks.test {
 useJUnitPlatform();jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")
 systemProperty("donorClasses",layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath)
 providers.gradleProperty("qaMenusJar").orNull?.let { renderer ->
  val jar=file(renderer);check(jar.isFile){"Installed renderer JAR required"}
  classpath=files(jar)+classpath;inputs.file(jar);systemProperty("qaMenusJar",jar.canonicalPath)
 }
 providers.gradleProperty("qaCoreJar").orNull?.let { candidate ->
  val jar=file(candidate);check(jar.isFile){"Actual candidate JAR required"}
  classpath=files(jar)+classpath;inputs.file(jar);systemProperty("qaCoreJar",jar.canonicalPath)
 }
}
fun registerPatch(task:String,entry:String)=tasks.register<JavaExec>(task){
 dependsOn(tasks.classes);classpath=sourceSets.main.get().runtimeClasspath;mainClass.set(entry)
 javaLauncher.set(javaToolchains.launcherFor {languageVersion.set(JavaLanguageVersion.of(21))})
 doFirst{args(providers.gradleProperty("patchArgs").get().split('|'))}
}
registerPatch("mergeFacade","MergeFactionFacade")
registerPatch("patchStaffRoute","StaffRoutePatch")
