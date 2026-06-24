---
name: ProGuard with JDK 25 no jmods
description: ProGuard obfuscation setup when JDK 25 (Adoptium) ships lib/modules but no jmods directory.
---

## Problem
Adoptium JDK 25 ships without a `jmods/` directory. ProGuard needs JDK class definitions to resolve class hierarchies — without them it throws "incomplete class hierarchy / superclass" even with `-dontwarn **`.

## Fix in proguard.pro
Add `-ignorewarnings` to suppress the superclass error from becoming fatal:
```
-dontwarn **
-dontnote **
-dontoptimize
-ignorewarnings
```

## Fix in build.gradle
Use `lib/modules` (the JImage file) instead of jmods:
```groovy
def javaHome = System.getProperty('java.home')
def modulesImg = file("${javaHome}/lib/modules")
def jmodsDir = file("${javaHome}/jmods")
if (modulesImg.exists()) {
    libraryjars("${javaHome}/lib/modules")
} else if (jmodsDir.exists()) {
    fileTree(jmodsDir).include('*.jmod').each { File jmod ->
        libraryjars(jmod, filter: '!**.jar,!module-info.class')
    }
}
```

## If obfuscate still fails
Decouple from build so the plain jar is produced:
```groovy
// build.dependsOn obfuscate  // comment out; run ./gradlew obfuscate separately
```
The plain `rocky-<version>.jar` is a fully functional Fabric mod — obfuscation is optional.

**Why:** ProGuard 7.x "superclass" errors are sometimes fatal even with -ignorewarnings when the JDK image format isn't fully parsed. The plain jar works identically at runtime.
