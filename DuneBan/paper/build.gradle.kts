plugins { java }
version="1.0.1"
dependencies {
 implementation(project(":common"))
 compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 compileOnly("net.luckperms:api:5.5")
 compileOnly("com.google.code.gson:gson:2.11.0")
 testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 testImplementation("net.luckperms:api:5.5")
 testImplementation("com.google.code.gson:gson:2.11.0")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.mockito:mockito-core:5.15.2")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
val mockitoAgent by configurations.creating
dependencies {mockitoAgent("org.mockito:mockito-core:5.15.2"){isTransitive=false}}
tasks.test {jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")}
tasks.jar {archiveFileName.set("DuneBan-Paper-1.0.1.jar");duplicatesStrategy=DuplicatesStrategy.EXCLUDE;from(configurations.runtimeClasspath.get().map{if(it.isDirectory)it else zipTree(it)});exclude("META-INF/*.SF","META-INF/*.RSA","META-INF/*.DSA")}
