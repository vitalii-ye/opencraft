package opencraft.execution;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LauncherCommandBuilderTest {

    @Test
    void buildContainsJavaPathFirst() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        List<String> command = builder.build();

        assertFalse(command.isEmpty());
        assertTrue(command.get(0).endsWith("java"), "First element should be java binary path");
    }

    @Test
    void buildContainsMainClass() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        List<String> command = builder.build();

        assertTrue(command.contains("net.minecraft.Main"));
    }

    @Test
    void jvmArgsComeBeforeMainClass() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        builder.addJvmArg("-Xmx2G");
        builder.addJvmArg("-Xms512M");

        List<String> command = builder.build();

        int xmxIndex = command.indexOf("-Xmx2G");
        int mainIndex = command.indexOf("net.minecraft.Main");
        assertTrue(xmxIndex < mainIndex, "JVM args should come before main class");
        assertTrue(command.contains("-Xms512M"));
    }

    @Test
    void gameArgsComeAfterMainClass() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        builder.addGameArg("--username");
        builder.addGameArg("Player");

        List<String> command = builder.build();

        int mainIndex = command.indexOf("net.minecraft.Main");
        int usernameIndex = command.indexOf("--username");
        assertTrue(usernameIndex > mainIndex, "Game args should come after main class");
    }

    @Test
    void classpathJoinedWithPathSeparator() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        builder.addClasspathEntry("/lib/a.jar");
        builder.addClasspathEntry("/lib/b.jar");

        List<String> command = builder.build();

        int cpFlagIndex = command.indexOf("-cp");
        assertTrue(cpFlagIndex >= 0, "Should contain -cp flag");
        String cpValue = command.get(cpFlagIndex + 1);
        assertEquals("/lib/a.jar" + File.pathSeparator + "/lib/b.jar", cpValue);
    }

    @Test
    void addClasspathEntriesAddsAll() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        builder.addClasspathEntries(Arrays.asList("/lib/x.jar", "/lib/y.jar", "/lib/z.jar"));

        List<String> command = builder.build();

        int cpFlagIndex = command.indexOf("-cp");
        String cpValue = command.get(cpFlagIndex + 1);
        assertTrue(cpValue.contains("/lib/x.jar"));
        assertTrue(cpValue.contains("/lib/y.jar"));
        assertTrue(cpValue.contains("/lib/z.jar"));
    }

    @Test
    void nativePathIncludedAsSystemProperty() {
        Path nativesDir = Path.of("/tmp/natives");
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main", nativesDir);

        List<String> command = builder.build();

        boolean hasNativeProp = command.stream()
                .anyMatch(arg -> arg.startsWith("-Djava.library.path=") && arg.contains("natives"));
        assertTrue(hasNativeProp, "Should contain -Djava.library.path system property");
    }

    @Test
    void noNativePathWhenNotProvided() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        List<String> command = builder.build();

        boolean hasNativeProp = command.stream()
                .anyMatch(arg -> arg.startsWith("-Djava.library.path="));
        assertFalse(hasNativeProp, "Should not contain -Djava.library.path when natives not set");
    }

    @Test
    void emptyStateProducesMinimalCommand() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        List<String> command = builder.build();

        // Should have: java, mainClass (at minimum)
        assertEquals(2, command.size());
    }

    @Test
    void noClasspathFlagWhenEmpty() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        List<String> command = builder.build();

        assertFalse(command.contains("-cp"), "Should not contain -cp when no classpath entries");
    }

    @Test
    void fluentApiReturnsThis() {
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main");
        LauncherCommandBuilder result = builder.addJvmArg("-Xmx2G");
        assertSame(builder, result);

        result = builder.addGameArg("--username");
        assertSame(builder, result);

        result = builder.addClasspathEntry("/lib/a.jar");
        assertSame(builder, result);

        result = builder.addClasspathEntries(Arrays.asList("/lib/b.jar"));
        assertSame(builder, result);
    }

    @Test
    void buildOrderIsCorrect() {
        Path nativesDir = Path.of("/tmp/natives");
        LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.Main", nativesDir);
        builder.addJvmArg("-Xmx2G");
        builder.addClasspathEntry("/lib/a.jar");
        builder.addGameArg("--username");
        builder.addGameArg("Player");

        List<String> command = builder.build();

        // Order should be: java, jvm args, natives, -cp, classpath, main class, game args
        int javaIndex = 0;
        int xmxIndex = command.indexOf("-Xmx2G");
        int cpIndex = command.indexOf("-cp");
        int mainIndex = command.indexOf("net.minecraft.Main");
        int usernameIndex = command.indexOf("--username");

        assertTrue(javaIndex < xmxIndex);
        assertTrue(xmxIndex < cpIndex);
        assertTrue(cpIndex < mainIndex);
        assertTrue(mainIndex < usernameIndex);
    }
}
