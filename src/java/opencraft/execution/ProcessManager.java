package opencraft.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ProcessManager {
    private Process process;
    private final Object lock = new Object();

    public void startProcess(List<String> command, Consumer<String> outputConsumer) throws IOException {
        synchronized (lock) {
            if (process != null && process.isAlive()) {
                throw new IllegalStateException("Process is already running");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            // Start a thread to read output
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (outputConsumer != null) {
                            outputConsumer.accept(line);
                        } else {
                            System.out.println(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            outputThread.setDaemon(true);
            outputThread.start();
        }
    }

    public boolean isRunning() {
        synchronized (lock) {
            return process != null && process.isAlive();
        }
    }

    public void waitFor() throws InterruptedException {
        Process p;
        synchronized (lock) {
            p = process;
        }
        if (p != null) {
            p.waitFor();
        }
    }
    
    public void stop() {
        synchronized (lock) {
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
