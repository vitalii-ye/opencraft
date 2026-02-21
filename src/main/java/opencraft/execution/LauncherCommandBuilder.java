package opencraft.execution;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LauncherCommandBuilder {
    private final String javaPath;
    private final List<String> jvmArgs;
    private final String mainClass;
    private final List<String> gameArgs;
    private final List<String> classpath;
    private final Path nativePath;

    public LauncherCommandBuilder(String mainClass) {
        this.mainClass = mainClass;
        this.javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        this.jvmArgs = new ArrayList<>();
        this.gameArgs = new ArrayList<>();
        this.classpath = new ArrayList<>();
        this.nativePath = null;
    }
    
    public LauncherCommandBuilder(String mainClass, Path nativePath) {
        this.mainClass = mainClass;
        this.javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        this.jvmArgs = new ArrayList<>();
        this.gameArgs = new ArrayList<>();
        this.classpath = new ArrayList<>();
        this.nativePath = nativePath;
    }

    public LauncherCommandBuilder addJvmArg(String arg) {
        jvmArgs.add(arg);
        return this;
    }

    public LauncherCommandBuilder addGameArg(String arg) {
        gameArgs.add(arg);
        return this;
    }

    public LauncherCommandBuilder addClasspathEntry(String entry) {
        classpath.add(entry);
        return this;
    }
    
    public LauncherCommandBuilder addClasspathEntries(List<String> entries) {
        classpath.addAll(entries);
        return this;
    }

    public List<String> build() {
        List<String> command = new ArrayList<>();
        command.add(javaPath);
        
        // Add JVM arguments
        command.addAll(jvmArgs);
        
        // Add natives path if present
        if (nativePath != null) {
            command.add("-Djava.library.path=" + nativePath.toAbsolutePath().toString());
        }
        
        // Add classpath
        if (!classpath.isEmpty()) {
            command.add("-cp");
            command.add(String.join(File.pathSeparator, classpath));
        }
        
        // Add main class
        command.add(mainClass);
        
        // Add game arguments
        command.addAll(gameArgs);
        
        return command;
    }
}
