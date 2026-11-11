# Galaxy Tunnel - Native Core AAR Directory

Place your `libv2ray.aar` file into this directory:
```
app/libs/libv2ray.aar
```

### How it works:
1. `app/build.gradle.kts` is already pre-configured to automatically include any `.aar` placed here:
   ```kotlin
   implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
   ```
2. `XrayCoreManager.kt` automatically discovers and links to `Libv2ray` methods via dynamic classpath resolution.
3. No code changes needed! Simply drop `libv2ray.aar` here and build with Gradle.
