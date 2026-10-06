// Embeds the nebs mod jar as the resource `nebs-mods/nebs-client-mod.jar` (see core's BundledMods),
// so front ends can install it into client templates without being pointed at the jar.
// Applied by :cli and :gradle-plugin.

val nebsMod: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    nebsMod(project(":mod"))
}

val bundleNebsMod = tasks.register<Sync>("bundleNebsMod") {
    from(nebsMod) { rename { "nebs-client-mod.jar" } }
    into(layout.buildDirectory.dir("bundled/nebs-mods"))
}

the<SourceSetContainer>()["main"].resources.srcDir(
    files(layout.buildDirectory.dir("bundled")).builtBy(bundleNebsMod),
)
