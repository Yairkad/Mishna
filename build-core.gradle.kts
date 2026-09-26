// Root build used when MISHNA_CORE_ONLY is set: no Android plugins, so the core builds without the Android SDK.
plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
}
