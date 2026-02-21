# OpenCraft Testing & Refactoring Progress

## Overview

Comprehensive unit and integration tests using JUnit 5 + Mockito for the OpenCraft Minecraft Launcher.
Tests cover business logic only (UI will be migrated to JavaFX later).

---

## Completed Work

### 1. Project Structure Migration

- [x] Moved `src/java/` to `src/main/java/` (Maven standard layout)
- [x] Moved `src/resources/` to `src/main/resources/`
- [x] Removed custom `<sourceDirectory>` and `<resources>` config from `pom.xml`
- [x] Added test dependencies to `pom.xml`:
  - `mockito-core` 5.12.0
  - `mockito-junit-jupiter` 5.12.0

### 2. Production Code Refactoring (Static to Instance-Based with DI)

All business logic classes were originally fully static with hardcoded dependencies on `MinecraftPathResolver`. Each was refactored to support constructor injection while preserving backward compatibility via a default no-arg constructor.

| Class | Changes |
|---|---|
| **`ConfigurationManager`** | Added `ConfigurationManager(Path configPath)` constructor; default chains to it |
| **`VersionCacheManager`** | Added `VersionCacheManager(Path cacheDir)` constructor; default chains to it |
| **`ModManager`** | Full rewrite: static → instance. Fields `baseModsDir`, `activeModsDir`. DI constructor accepts paths. `isModFile` made package-private |
| **`ShaderManager`** | Full rewrite: static → instance. Fields `shaderpacksDir`, `ModManager modManager`. DI constructor accepts paths + modManager. `isShaderFile` made package-private |

### 3. UI Caller Updates

All UI classes that referenced the now-instance methods were updated:

| File | Changes |
|---|---|
| **`OpenCraftLauncher.java`** | Line 819: `ModManager.syncModsToDirectory(...)` → `new ModManager().syncModsToDirectory(...)` |
| **`UnifiedModsDialog.java`** | Added `modManager` / `shaderManager` fields, replaced 9 static calls, fixed SwingWorker diamond operator |
| **`ModsDialog.java`** | Added `modManager` field, replaced 4 static calls, removed unused `ModrinthApiClient` import, fixed SwingWorker diamond operator |
| **`ShadersDialog.java`** | Added `shaderManager` field, replaced 5 static calls, fixed SwingWorker diamond operator |

### 4. Bug Fix — SwingWorker Diamond Operator

Anonymous `SwingWorker<InstalledMod, String>` classes that override `process(List<String>)` cause a name clash error with diamond operator `new SwingWorker<>()` under `-Xlint:all -Werror`. Fixed by using explicit type parameters in `UnifiedModsDialog.java`, `ModsDialog.java`, and `ShadersDialog.java`.

### 5. Test Files Created (12 files)

All test files are written to `src/test/java/opencraft/`:

| # | Test File | Type | What It Tests |
|---|---|---|---|
| 1 | `mods/InstalledModTest.java` | Unit | Display name extraction, version extraction, formatted size, `fromFile` factory, type checks, `toString` |
| 2 | `execution/LauncherCommandBuilderTest.java` | Unit | Build ordering, JVM args, classpath joining, native path, game args, empty state, fluent API |
| 3 | `execution/ProcessManagerTest.java` | Integration | Start/stop process, output capture, already-running exception, waitFor, working directory |
| 4 | `utils/MinecraftPathResolverTest.java` | Unit | Path structure validation for current OS, subdirectory methods |
| 5 | `utils/ConfigurationManagerTest.java` | Integration (temp dir) | Default values, save/load round-trip, missing file handling |
| 6 | `network/VersionCacheManagerTest.java` | Integration (temp dir) | Save/retrieve, TTL expiry, needsValidation, getStoredETag, clearCache, corrupt JSON |
| 7 | `network/MinecraftVersionManagerTest.java` | Unit (models) | `MinecraftVersion`: vanilla/fabric constructors, display names, type checks, `toFabricVersion`. `VersionResponse`: `isNotModified` |
| 8 | `network/ModrinthApiClientTest.java` | Unit (models) | `ModrinthProject`, `ModrinthVersion` (`getPrimaryFile`), `ModrinthFile` |
| 9 | `network/FabricVersionManagerTest.java` | Unit (models) | `FabricLoaderVersion`, `FabricGameVersion`, `FabricVersion` (versionId, displayName, profileUrl) |
| 10 | `mods/ModManagerTest.java` | Integration (temp dir) | `getInstalledMods`, empty dir, `isModInstalled`, `removeMod`, `syncModsForVersion`, `isModFile` filtering |
| 11 | `mods/ShaderManagerTest.java` | Integration (temp dir) | `getInstalledShaders`, empty dir, `removeShader`, `isIrisInstalled`, `isSodiumInstalled`, `isShaderFile` filtering |
| 12 | `network/FabricDownloaderTest.java` | Unit | `getFabricVersionId`, `isFabricInstalled` (temp dir) |

### 6. Production Build Verified

`mvn compile -q` passes cleanly with zero errors under `-Xlint:all -Werror`.

### 7. Test Execution & Fixes

- [x] **All 12 test files compile and pass** — `mvn test` runs successfully
- [x] **Fixed `InstalledModTest.fromFileWithZipExtension`** — Test asserted version `"8.2"` for filename `BSL_v8.2.09.zip`, but `extractVersion` regex (`\d+\.\d+(?:\.\d+)?`) correctly matches `"8.2.09"`. Fixed assertion to expect `"8.2.09"`.

---

## Current Status

**All work is complete.** Production code compiles cleanly and all tests pass. Changes are uncommitted.

## Next Steps

- [ ] **Commit all changes** — Refactoring + tests are ready to commit
- [ ] **Add CI integration** — Consider adding `mvn test` to the GitHub Actions release workflow
- [ ] **Increase coverage** — Future test candidates:
  - `MinecraftDownloader` (would need HTTP mocking or further refactoring)
  - `FabricLauncher` (depends on process execution)
  - End-to-end integration tests for the full launch flow

---

## Technical Notes

- **Java version**: 11
- **Compiler flags**: `-Xlint:all -Werror` (applies to both production and test code)
- **Testing approach**: Constructor injection (Full DI), no `mockStatic`
- **Network tests**: Only inner model classes tested; methods making HTTP calls are skipped
- **Platform**: Tests using shell commands (`echo`, `sleep`, `pwd` in `ProcessManagerTest`) work on macOS/Linux only
- **LSP stale errors**: Any LSP errors referencing `src/java/opencraft/...` are stale diagnostics from the old directory layout — they are harmless and can be ignored

---

## Modified Files Summary

### Production Code (9 files)
```
pom.xml
src/main/java/opencraft/OpenCraftLauncher.java
src/main/java/opencraft/mods/ModManager.java
src/main/java/opencraft/mods/ShaderManager.java
src/main/java/opencraft/network/VersionCacheManager.java
src/main/java/opencraft/utils/ConfigurationManager.java
src/main/java/opencraft/ui/UnifiedModsDialog.java
src/main/java/opencraft/ui/ModsDialog.java
src/main/java/opencraft/ui/ShadersDialog.java
```

### Test Code (12 files)
```
src/test/java/opencraft/mods/InstalledModTest.java
src/test/java/opencraft/mods/ModManagerTest.java
src/test/java/opencraft/mods/ShaderManagerTest.java
src/test/java/opencraft/utils/MinecraftPathResolverTest.java
src/test/java/opencraft/utils/ConfigurationManagerTest.java
src/test/java/opencraft/execution/LauncherCommandBuilderTest.java
src/test/java/opencraft/execution/ProcessManagerTest.java
src/test/java/opencraft/network/MinecraftVersionManagerTest.java
src/test/java/opencraft/network/VersionCacheManagerTest.java
src/test/java/opencraft/network/ModrinthApiClientTest.java
src/test/java/opencraft/network/FabricVersionManagerTest.java
src/test/java/opencraft/network/FabricDownloaderTest.java
```
