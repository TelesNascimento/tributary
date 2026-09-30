rootProject.name = "tributary"

val ibmPlugins = providers.gradleProperty("ibmPlugins").orNull
if (ibmPlugins != null && file(ibmPlugins).isDirectory) {
    include(":bridge")
}
