package opencraft.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessManagerTest {

    private final ProcessManager processManager = new ProcessManager();

    @AfterEach
    void tearDown() {
        processManager.stop();
    }

    @Test
    void startAndCaptureOutput() throws IOException, InterruptedException {
        List<String> output = Collections.synchronizedList(new ArrayList<>());

        processManager.startProcess(
                Arrays.asList("echo", "hello world"),
                output::add
        );
        processManager.waitFor();

        // Give the output thread time to flush
        Thread.sleep(200);

        assertFalse(output.isEmpty(), "Should capture output");
        assertTrue(output.get(0).contains("hello world"));
    }

    @Test
    void isRunningReturnsFalseBeforeStart() {
        assertFalse(processManager.isRunning());
    }

    @Test
    void isRunningReturnsTrueWhileProcessRuns() throws IOException {
        // Start a long-running process
        processManager.startProcess(
                Arrays.asList("sleep", "60"),
                null
        );

        assertTrue(processManager.isRunning());
    }

    @Test
    void stopKillsRunningProcess() throws IOException, InterruptedException {
        processManager.startProcess(
                Arrays.asList("sleep", "60"),
                null
        );

        assertTrue(processManager.isRunning());

        processManager.stop();

        // Give it a moment to terminate
        Thread.sleep(500);
        assertFalse(processManager.isRunning());
    }

    @Test
    void throwsIfProcessAlreadyRunning() throws IOException {
        processManager.startProcess(
                Arrays.asList("sleep", "60"),
                null
        );

        assertThrows(IllegalStateException.class, () ->
                processManager.startProcess(
                        Arrays.asList("echo", "second"),
                        null
                )
        );
    }

    @Test
    void waitForCompletesWhenProcessEnds() throws IOException, InterruptedException {
        processManager.startProcess(
                Arrays.asList("echo", "done"),
                null
        );

        // Should not hang
        processManager.waitFor();
        assertFalse(processManager.isRunning());
    }

    @Test
    void workingDirectoryIsRespected(@TempDir Path tempDir) throws IOException, InterruptedException {
        List<String> output = Collections.synchronizedList(new ArrayList<>());

        processManager.startProcess(
                Arrays.asList("pwd"),
                output::add,
                tempDir.toFile()
        );
        processManager.waitFor();
        Thread.sleep(200);

        assertFalse(output.isEmpty());
        assertTrue(output.get(0).contains(tempDir.getFileName().toString()));
    }

    @Test
    void waitForWithNullProcessDoesNotThrow() throws InterruptedException {
        // Should handle null process gracefully
        processManager.waitFor();
    }

    @Test
    void stopWithNoProcessDoesNotThrow() {
        // Should handle no running process gracefully
        assertDoesNotThrow(() -> processManager.stop());
    }

    @Test
    void canStartNewProcessAfterPreviousCompletes() throws IOException, InterruptedException {
        processManager.startProcess(
                Arrays.asList("echo", "first"),
                null
        );
        processManager.waitFor();
        Thread.sleep(100);

        // Should be able to start another
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        processManager.startProcess(
                Arrays.asList("echo", "second"),
                output::add
        );
        processManager.waitFor();
        Thread.sleep(200);

        assertTrue(output.stream().anyMatch(line -> line.contains("second")));
    }
}
