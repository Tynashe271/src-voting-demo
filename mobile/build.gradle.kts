plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

// Coil3's own dependency graph asks for a newer skiko-awt (JVM API jar) than Compose Multiplatform
// 1.7.1 ships by default, but Gradle's conflict resolution bumped only that artifact and left the
// *native* skiko-awt-runtime-<platform> binaries on the old version -- a java.lang.UnsatisfiedLinkError
// at runtime (RenderNodeContext_nMake), since the JVM class and the native .dll it calls into no
// longer agree on their function signatures. Forcing every skiko artifact to the same version keeps
// the Kotlin/JVM side and the native side in lockstep.
allprojects {
    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.jetbrains.skiko") {
                    useVersion("0.9.4")
                }
            }
        }
    }
}
