
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// jar 文件名（不改 modid）：gt-perfect-overclock-<版本>.jar，版本取自 gradle.properties 的 RELEASE_VERSION
base {
    archivesName.set("gt-perfect-overclock")
}
