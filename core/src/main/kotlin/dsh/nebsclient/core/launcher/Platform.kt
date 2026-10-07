package dsh.nebsclient.core.launcher

/** The host OS in the vocabulary used by Mojang's metadata. */
internal data class Platform(val os: String, val arch: String) {
    val is64Bit get() = arch.contains("64")
    val isArm64 get() = arch == "aarch64" || arch == "arm64"

    /** Key of this platform in Mojang's Java runtime index. */
    val runtimeKey: String
        get() = when (os) {
            "osx" -> if (isArm64) "mac-os-arm64" else "mac-os"
            "windows" -> when {
                isArm64 -> "windows-arm64"
                is64Bit -> "windows-x64"
                else -> "windows-x86"
            }
            else -> if (is64Bit) "linux" else "linux-i386"
        }

    companion object {
        val current: Platform by lazy {
            val name = System.getProperty("os.name").lowercase()
            val os = when {
                "mac" in name || "darwin" in name -> "osx"
                "win" in name -> "windows"
                else -> "linux"
            }
            Platform(os, System.getProperty("os.arch").lowercase())
        }
    }
}
