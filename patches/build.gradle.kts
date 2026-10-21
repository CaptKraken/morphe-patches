import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "app.morphe"

patches {
    about {
        name = "Morphe Patches"
        description = "Patches for Morphe"
        source = "git@github.com:MorpheApp/morphe-patches.git"
        author = "MorpheApp"
        contact = "na"
        website = "https://morphe.software"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")
val jamApk = providers.gradleProperty("jamApk")
val jamOutput = providers.gradleProperty("jamOutput")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)

    patchListGeneratorClasspath(libs.gson)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.protobuf.javalite)
    testImplementation("org.json:json:20240303")
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
        dependsOn(":extensions:youtube:generateReleaseProto")
        jamApk.orNull?.let { systemProperty("jamApk", it) }
        // The focused state tests compile this Android-free extension source directly.
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/PlaylistQueue.java"
        )).withPropertyName("playlistQueueStateSource")
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueShortsSession.java"
        )).withPropertyName("playlistQueueShortsSessionSource")
        inputs.files(
            rootProject.file("extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/VideoMetadata.java"),
            rootProject.file("extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/queue_card.proto")
        ).withPropertyName("playlistQueueMetadataSources")
        inputs.files(
            rootProject.file("extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueRatingActions.java"),
            rootProject.file("extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/playlist_queue.proto"),
            rootProject.file("extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/next_response.proto"),
            rootProject.file("extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/icon.proto")
        ).withPropertyName("playlistQueueRatingSources")
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveActions.java"
        )).withPropertyName("playlistQueueSaveActionsSource")
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveFeedback.java"
        )).withPropertyName("playlistQueueSaveFeedbackSource")
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueUndoActions.java"
        )).withPropertyName("playlistQueueUndoActionsSource")
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueStart.java"
        )).withPropertyName("playlistQueueStartSource")
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/OpenVideosFullscreenHookPatch.java"
        )).withPropertyName("playlistQueueFullscreenSource")
    }

    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.patches.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    register<JavaExec>("validateJam") {
        description = "Patches a supplied YouTube Music APK for Jam validation and optional device testing"
        group = "verification"

        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.music.interaction.jam.JamDeviceBuildKt")

        doFirst {
            val apk = jamApk.orNull
                ?: throw GradleException("validateJam requires -PjamApk=/absolute/path/to/ytm.apk")
            setArgs(listOfNotNull(apk, jamOutput.orNull))
        }
    }

    register<JavaExec>("validatePlaylistQueue") {
        description = "Applies Native playlist queue to a stock APK and verifies it with Android SDK tools"
        group = "verification"
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.youtube.interaction.nativeplaylistqueue.PlaylistQueueApkBuildKt")
        doFirst {
            val apk = providers.gradleProperty("playlistQueueApk").orNull
                ?: throw GradleException("validatePlaylistQueue requires -PplaylistQueueApk=/absolute/path/youtube.apk")
            val mode = providers.gradleProperty("playlistQueueMode").getOrElse("standalone")
            val output = providers.gradleProperty("playlistQueueOutput").orNull
            setArgs(listOfNotNull(apk, mode, output))
        }
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
