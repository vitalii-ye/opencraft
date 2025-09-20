# OpenCraft - Minecraft Downloader and Launcher

This project contains tools for downloading and launching Minecraft.

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

## Building the Project

```bash
mvn compile
```

## Running the Applications

### Download Minecraft Files

To download Minecraft 1.21 files (client JAR, libraries, assets index, and all asset objects):

```bash
mvn compile exec:java
```

**Note**: This downloads approximately 3,905 asset files (textures, sounds, models, etc.) which takes several minutes. The process shows progress every 100 downloaded files.

Or specifically run the downloader:

```bash
mvn compile exec:java@downloader
```

**Important**: The downloader now automatically creates a `minecraft/libraries.txt` file containing the correct classpath for your operating system. This file is essential for the launcher to work properly.

### Launch the GUI Application

To start the graphical launcher:

```bash
mvn compile exec:java
```

### Test Launcher Setup

You can verify that all files are properly downloaded and the launcher setup is correct:

```bash
mvn compile exec:java@verify
```

The launcher can also be tested to verify the setup (it will attempt to launch Minecraft but will fail without proper assets and authentication):

```bash
mvn compile exec:java@launcher
```

**Note**: To actually run Minecraft, you would need:
1. Complete asset files downloaded (the downloader currently only downloads the assets index)
2. A valid Minecraft account authentication token
3. Proper native libraries for your platform

The current launcher serves as a demonstration of how to set up the classpath and launch arguments.

## Project Structure

- `src/main/java/com/opencraft/MinecraftDownloader.java` - Downloads Minecraft files
- `src/main/java/com/opencraft/MinecraftLauncher.java` - Launches Minecraft with downloaded files
- `pom.xml` - Maven configuration with dependencies and exec plugin

## What Gets Downloaded

The downloader will create a `minecraft` directory with:
- `versions/1.21/1.21.jar` - Main Minecraft client JAR
- `versions/1.21/1.21.json` - Version manifest with game configuration
- `libraries/` - All required library JARs (~98 libraries)
- `assets/indexes/17.json` - Asset index file (asset ID "17" for Minecraft 1.21)  
- `assets/objects/` - All game assets organized by hash (3,905 files including textures, sounds, models, etc.)

The launcher will use these files to start Minecraft with the proper classpath and JVM arguments.

## Troubleshooting

### "minecraft/libraries.txt not found!" Error

If you encounter this error when launching Minecraft:

```
Error: minecraft/libraries.txt not found!
Please run the downloader first.
```

**Solution**: Run the downloader first to create the necessary files:

```bash
mvn compile exec:java@downloader
```

This error occurs because the launcher requires a `libraries.txt` file that contains the classpath for all Minecraft libraries. The downloader automatically creates this file with the correct path separators for your operating system (`:` on Unix/Linux/macOS, `;` on Windows).

### Classpath Issues on Windows

If Minecraft fails to start on Windows with classpath-related errors, ensure you're using the latest version of the downloader, which correctly generates OS-specific path separators in the `libraries.txt` file.

### Fresh Installation

For a completely fresh installation:

1. Delete the `minecraft` directory if it exists
2. Run the downloader: `mvn compile exec:java@downloader`
3. Launch the application: `mvn compile exec:java`
