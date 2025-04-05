package org.forever.demo;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

public class ExternalUpdater {
    private static final Logger logger = Logger.getLogger(ExternalUpdater.class.getName());
    private static final String JAR_NAME = "demo-1.0-SNAPSHOT.jar";
    private static final String UPDATE_FILE = "update.jar";
    private static final String BACKUP_JAR = "backup_" + JAR_NAME;
    private static final int MAX_ATTEMPTS = 10;
    private static final int ATTEMPT_DELAY_MS = 1000;
    private static final String LOG_DIR = "logs";

    static {
        try {
            // Отключаем передачу логов родительскому логгеру
            logger.setUseParentHandlers(false);

            // Создание директории логов, если она не существует
            File logDir = new File(LOG_DIR);
            if (!logDir.exists() && !logDir.mkdirs()) {
                System.err.println("Не удалось создать папку для логов: " + logDir.getAbsolutePath());
            }

            // Проверяем и удаляем external_updater.log в корневом каталоге
            File rootLogFile = new File("external_updater.log");
            if (rootLogFile.exists() && rootLogFile.delete()) {
                System.out.println("Удален лог-файл из корневого каталога: external_updater.log");
            }

            // Указываем путь к файлу лога внутри папки logs
            File logFile = new File(logDir, "external_updater.log");
            FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), false);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);

            // Также логируем в консоль
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(consoleHandler);

            logger.info("External updater logger initialized");
        } catch (IOException e) {
            System.err.println("Error initializing logger: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ExternalUpdater <wait_time_seconds>");
            System.exit(1);
        }

        try {
            // Give the main application time to exit completely
            int waitTimeSeconds = Integer.parseInt(args[0]);
            logger.info("Waiting " + waitTimeSeconds + " seconds for main application to exit...");
            Thread.sleep(waitTimeSeconds * 1000L);

            // On Windows, try batch file approach instead of direct update
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                createAndRunBatchUpdate();
            } else {
                // Apply the update directly on non-Windows systems
                applyUpdate();
                // Restart the application
                restartApplication();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in external updater", e);
            System.exit(1);
        }
    }

    private static void createAndRunBatchUpdate() throws IOException {
        logger.info("Creating Windows batch file for update...");

        // Create batch file content
        String batchContent =
                "@echo off\r\n" +
                        "echo Starting update...\r\n" +
                        "timeout /t 2 /nobreak > nul\r\n" +  // Wait 2 seconds

                        // Delete backup if exists
                        "if exist " + BACKUP_JAR + " (\r\n" +
                        "    echo Deleting old backup...\r\n" +
                        "    del /f /q " + BACKUP_JAR + "\r\n" +
                        "    if exist " + BACKUP_JAR + " (\r\n" +
                        "        echo Could not delete backup, aborting.\r\n" +
                        "        exit /b 1\r\n" +
                        "    ) else (\r\n" +
                        "        echo Old backup deleted.\r\n" +
                        "    )\r\n" +
                        ")\r\n" +

                        // Backup current JAR
                        "if exist " + JAR_NAME + " (\r\n" +
                        "    echo Creating backup...\r\n" +
                        "    ren " + JAR_NAME + " " + BACKUP_JAR + "\r\n" +
                        "    if exist " + JAR_NAME + " (\r\n" +
                        "        echo Failed to rename current JAR, aborting.\r\n" +
                        "        exit /b 1\r\n" +
                        "    ) else (\r\n" +
                        "        echo Backup created successfully.\r\n" +
                        "    )\r\n" +
                        ")\r\n" +

                        // Move update file to main JAR
                        "if exist " + UPDATE_FILE + " (\r\n" +
                        "    echo Installing update...\r\n" +
                        "    ren " + UPDATE_FILE + " " + JAR_NAME + "\r\n" +
                        "    if exist " + UPDATE_FILE + " (\r\n" +
                        "        echo Failed to rename update file.\r\n" +
                        "        echo Attempting to restore from backup...\r\n" +
                        "        ren " + BACKUP_JAR + " " + JAR_NAME + "\r\n" +
                        "        exit /b 1\r\n" +
                        "    ) else (\r\n" +
                        "        echo Update installed successfully.\r\n" +
                        "    )\r\n" +
                        ")\r\n" +

                        // Start application
                        "echo Starting application...\r\n" +
                        "start javaw --module-path libwindows --add-modules javafx.controls,javafx.fxml -jar " + JAR_NAME + "\r\n" +
                        "echo Update completed successfully.\r\n" +
                        "exit\r\n";

        // Write batch file
        Path batchFile = Paths.get("perform_update.bat");
        Files.write(batchFile, batchContent.getBytes());
        logger.info("Created batch file: " + batchFile.toAbsolutePath());

        // Run batch file
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "start", batchFile.toString());
            builder.start();
            logger.info("Batch update started, exiting updater...");
            // Give time for the batch file to start
            Thread.sleep(1000);
            System.exit(0);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start batch update", e);
            throw new IOException("Failed to start batch update: " + e.getMessage());
        }
    }

    private static void applyUpdate() throws IOException, InterruptedException {
        logger.info("Starting update application...");
        Path currentJar = Paths.get(JAR_NAME);
        Path backupJar = Paths.get(BACKUP_JAR);
        Path newJar = Paths.get(UPDATE_FILE);

        // Check if files exist
        logger.info("Current JAR exists: " + Files.exists(currentJar));
        logger.info("New JAR exists: " + Files.exists(newJar));

        // Validate the update file first
        if (!Files.exists(newJar)) {
            throw new IOException("Update file not found: " + newJar);
        }

        // Delete old backup if it exists
        if (Files.exists(backupJar)) {
            boolean deleted = deleteWithRetry(backupJar);
            if (!deleted) {
                logger.warning("Could not delete old backup, but continuing anyway");
            } else {
                logger.info("Deleted old backup: " + backupJar);
            }
        }

        // Rename current JAR to backup with retry
        boolean moveSuccess = moveWithRetry(currentJar, backupJar);
        if (!moveSuccess) {
            throw new IOException("Failed to backup current JAR after multiple attempts");
        }
        logger.info("Current JAR backed up: " + backupJar);

        // Move downloaded JAR to working directory
        moveSuccess = moveWithRetry(newJar, currentJar);
        if (!moveSuccess) {
            // Try to restore from backup if move failed
            logger.warning("Failed to move new JAR. Attempting to restore from backup...");
            moveWithRetry(backupJar, currentJar);
            throw new IOException("Failed to move new JAR after multiple attempts");
        }
        logger.info("New JAR installed: " + currentJar);

        // Verify files after moving
        logger.info("After update - JAR exists: " + Files.exists(currentJar));
        logger.info("After update - Backup exists: " + Files.exists(backupJar));
    }

    private static boolean deleteWithRetry(Path path) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Files.delete(path);
                return true;
            } catch (IOException e) {
                logger.warning("Delete attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(ATTEMPT_DELAY_MS);
                }
            }
        }
        return false;
    }

    private static boolean moveWithRetry(Path source, Path target) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // On Windows, try to use copy + delete instead of move
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    // First try to delete the target if it exists
                    if (Files.exists(target)) {
                        try {
                            Files.delete(target);
                            logger.info("Successfully deleted target file: " + target);
                        } catch (IOException e) {
                            logger.warning("Failed to delete target file: " + e.getMessage());
                            // Continue anyway as copy with REPLACE will overwrite
                        }
                    }

                    // Copy file and then delete source
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(source);
                } else {
                    // On other systems, move works fine
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException e) {
                logger.warning("Move attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    // Увеличиваем задержку между попытками на Windows
                    Thread.sleep(ATTEMPT_DELAY_MS * 2);
                }
            }
        }
        return false;
    }

    private static void restartApplication() throws IOException {
        logger.info("Restarting application...");

        File jarFile = new File(JAR_NAME);
        if (!jarFile.exists()) {
            String errorMsg = "JAR file not found: " + jarFile.getAbsolutePath();
            logger.severe(errorMsg);
            throw new IOException(errorMsg);
        }

        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String javaExec = isWindows ? "javaw" : "java";
        String javaPath = javaBin + javaExec;

        String libPath = isWindows ? "libwindows" : "liblinux";

        File javaFile = new File(javaPath);
        if (!javaFile.exists()) {
            logger.warning("Java executable not found at: " + javaPath);
            javaPath = javaExec;
        }

        logger.info("Using Java: " + javaPath);
        logger.info("Launching JAR: " + jarFile.getAbsolutePath());
        logger.info("Using JavaFX libraries from: " + libPath);
        logger.info("Current directory: " + new File(".").getAbsolutePath());

        try {
            List<String> command = new ArrayList<>();
            command.add(javaPath);
            command.add("--module-path");
            command.add(libPath);
            command.add("--add-modules");
            command.add("javafx.controls,javafx.fxml");
            command.add("-jar");
            command.add(jarFile.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(command);

            // Создание папки logs, если её нет
            File logsDir = new File("logs");
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                logger.warning("Не удалось создать директорию logs");
            }

            // Перенаправление вывода в logs/restart.log
            File logFile = new File(logsDir, "restart.log");
            if (logFile.exists() && !logFile.delete()) {
                logger.warning("Failed to delete file: " + logFile.getAbsolutePath());
            }
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile);

            logger.info("Launch command: " + String.join(" ", command));

            Process process = builder.start();
            Thread.sleep(1000);

            if (process.isAlive()) {
                logger.info("New process started successfully");
            } else {
                logger.warning("New process failed to start, exit code: " + process.exitValue());
            }

            logger.info("External updater completed successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error restarting application", e);
            throw new IOException("Error restarting: " + e.getMessage(), e);
        }
    }


    //Очистка файла логов
    private static void cleanupLogFile() {
        try {
            // Удаляем файл лога из корневого каталога
            File rootLogFile = new File("external_updater.log");
            if (rootLogFile.exists()) {
                if (rootLogFile.delete()) {
                    System.out.println("Удален файл лога из корневого каталога: external_updater.log");
                } else {
                    System.err.println("Не удалось удалить файл лога: external_updater.log");
                }
            }
        } catch (Exception e) {
            System.err.println("Error cleaning up log file: " + e.getMessage());
        }
    }
}