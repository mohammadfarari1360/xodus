dependencies {
    api(project(":xodus-openAPI"))
    implementation(project(":xodus-utils"))
    implementation(project(":xodus-environment"))
    implementation(project(":xodus-compress"))
    implementation(libs.commons.io)
    testImplementation("io.github.classgraph:classgraph:4.8.90")
    testImplementation(project(":xodus-utils-test"))
}

val testArtifacts: Configuration by configurations.creating

tasks {
    val jarTest by creating(Jar::class) {
        archiveClassifier.set("test")
        from(sourceSets.test.get().output)
    }

    artifacts {
        add("testArtifacts", jarTest)
    }
}