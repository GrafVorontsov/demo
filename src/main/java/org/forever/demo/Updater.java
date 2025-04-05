package org.forever.demo;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.logging.*;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Updater {

    // Добавляем логгер
    private static final Logger logger = Logger.getLogger(Updater.class.getName());

    // Updated URLs for GitHub - теперь используем заранее подготовленный прямой URL
    private static final String VERSION_URL = "https://raw.githubusercontent.com/GrafVorontsov/demo/master/version.txt";
    private static final String UPDATE_URL = "https://github.com/GrafVorontsov/demo/releases/download/v{VERSION}/demo-1.0-SNAPSHOT.jar";
    private static final String JAR_NAME = "demo-1.0-SNAPSHOT.jar"; // Название JAR-файла
    private static final String UPDATE_FILE = "update.jar"; // Временный файл для загрузки
    private static final long EXPECTED_FILE_SIZE = 30 * 1024 * 1024; // Примерно 30 МБ
    private static final long MIN_VALID_SIZE = 1024 * 1024; // Минимум 1 МБ для валидного JAR (уменьшили требования)

    private static Stage progressStage;
    private static ProgressBar progressBar;
    private static Label statusLabel;
    private static Label percentLabel;

    private static final IntegerProperty downloadPercent = new SimpleIntegerProperty(0);

    private static final String LOG_DIR = "logs";
    private static final String[] LOG_FILES = {
            "updater.log",
            "external_updater.log",
            "updater_launch.log",
            "restart.log"
    };

    // Удалить файл update.log вне папки logs, если он существует
    private static final String OUTSIDE_LOG_FILE = "update.log";

    static {
        logger.setUseParentHandlers(false);
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            if (logDir.mkdirs()) {
                System.out.println("Создана папка для логов: " + logDir.getAbsolutePath());
            } else {
                System.err.println("Не удалось создать папку для логов: " + logDir.getAbsolutePath());
            }
        }

        // Очистить все лог-файлы
        for (String logFile : LOG_FILES) {
            cleanupLogFile(new File(logDir, logFile).getPath());
        }

        // Дополнительно проверяем и удаляем updater.log в корневом каталоге
        cleanupLogFile("updater.log");

        try {
            File logFile = new File(logDir, "updater.log");
            FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), false);
            SimpleFormatter formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);
            logger.addHandler(fileHandler);
            logger.info("Логгер успешно инициализирован");
        } catch (IOException e) {
            System.err.println("Ошибка при инициализации логгера: " + e.getMessage());
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(consoleHandler);
            logger.log(Level.SEVERE, "Не удалось создать файловый обработчик логов", e);
        }
    }

    /**
     * Очищает указанный файл лога, если он существует
     */
    private static void cleanupLogFile(String filePath) {
        try {
            File logFile = new File(filePath);
            if (logFile.exists()) {
                if (logFile.delete()) {
                    System.out.println("Удален старый файл логов: " + filePath);
                } else {
                    System.err.println("Не удалось удалить старый файл логов: " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при очистке файла логов: " + e.getMessage());
        }
    }

    // Метод для асинхронной проверки обновлений
    public static void checkForUpdatesAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                checkForUpdatesInternal();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Ошибка при проверке обновлений", e);
            }
        });
    }

    // Внутренний метод, который выполняет проверку
    private static void checkForUpdatesInternal() {
        try {
            logger.info("Начало проверки обновлений");
            String latestVersion = getLatestVersion();
            logger.info("Последняя версия: " + latestVersion);

            if (latestVersion == null) {
                logger.warning("Не удалось получить информацию о последней версии");
                return; // Тихий выход при ошибке
            }

            String currentVersion = getCurrentVersion();
            logger.info("Текущая версия: " + currentVersion);

            if (currentVersion == null) {
                logger.warning("Не удалось получить информацию о текущей версии");
                return; // Тихий выход при ошибке
            }

            if (!latestVersion.equals(currentVersion)) {
                logger.info("Найдено обновление: " + latestVersion);
                // Показываем диалог в потоке UI
                Platform.runLater(() -> showUpdateDialog(latestVersion));
            } else {
                logger.info("Обновление не требуется. Текущая версия: " + currentVersion);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при проверке обновлений", e);
        }
    }

    // Показать диалог с предложением обновления
    private static void showUpdateDialog(String newVersion) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Доступно обновление");
        alert.setHeaderText("Доступна новая версия: " + newVersion);
        alert.setContentText("Желаете обновить приложение сейчас?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            logger.info("Пользователь согласился на обновление до версии " + newVersion);
            // Пользователь согласился на обновление
            // показываем анимированный диалог загрузки
            Platform.runLater(Updater::showProgressDialog);

            // Запускаем скачивание в отдельном потоке
            downloadWithProgress(newVersion);
        } else {
            logger.info("Пользователь отказался от обновления до версии " + newVersion);
        }
    }

    // Показать диалог с индикатором прогресса
    private static void showProgressDialog() {
        progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.initStyle(StageStyle.DECORATED);
        progressStage.setTitle("Загрузка обновления");
        progressStage.setResizable(false);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        statusLabel = new Label("Подготовка к загрузке...");
        percentLabel = new Label("0%");

        Button cancelButton = createCancelButton();

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(statusLabel, progressBar, percentLabel, cancelButton);

        Scene scene = new Scene(layout);
        progressStage.setScene(scene);
        progressStage.show();
    }

    private static Button createCancelButton() {
        Button cancelButton = new Button("Отмена");
        cancelButton.setOnAction(_ -> {
            logger.info("Загрузка обновления отменена пользователем");
            progressStage.close();
            // Очистка частично загруженного файла
            File updateFile = new File(UPDATE_FILE);
            if (updateFile.exists()) {
                boolean deleted = updateFile.delete();
                if (deleted) {
                    logger.info("Временный файл обновления успешно удален: " + UPDATE_FILE);
                } else {
                    logger.warning("Не удалось удалить временный файл обновления: " + UPDATE_FILE);
                }
            }
        });
        return cancelButton;
    }

    // Загрузка с отображением прогресса (обновленная для GitHub)
    @SuppressWarnings("AssignmentToForLoopParameter")
    private static void downloadWithProgress(String version) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                File updateFile = new File(UPDATE_FILE);
                String downloadUrl = UPDATE_URL.replace("{VERSION}", version.trim());

                logger.info("Начало загрузки обновления с URL: " + downloadUrl);

                // Удаляем предыдущий файл обновления, если существует
                if (updateFile.exists()) {
                    if (!updateFile.delete()) {
                        logger.warning("Не удалось удалить файл: " + updateFile.getAbsolutePath());
                    }
                    logger.info("Удален предыдущий файл обновления");
                }

                try {
                    updateMessage("Подключение к серверу...");
                    logger.info("Подключение к серверу...");

                    // Создаем HttpClient
                    HttpClient httpClient = HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofSeconds(15))
                            .build();

                    // Создаем запрос
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl))
                            .timeout(Duration.ofSeconds(30))
                            .header("User-Agent", "Mozilla/5.0 (compatible; ApplicationUpdater/1.0)")
                            .header("Accept", "application/octet-stream")
                            .GET()
                            .build();

                    logger.info("Выполняется HTTP запрос...");

                    // Выполняем запрос и получаем ответ
                    HttpResponse<InputStream> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofInputStream());

                    // Проверяем код ответа
                    int responseCode = response.statusCode();
                    logger.info("Код ответа: " + responseCode);

                    if (responseCode != 200) {
                        logger.severe("Ошибка HTTP: " + responseCode);
                        updateMessage("Ошибка сервера: " + responseCode);
                        return false;
                    }

                    // Получаем размер файла из заголовков, если доступен
                    OptionalLong contentLengthOpt = response.headers().firstValueAsLong("Content-Length");
                    long contentLength = contentLengthOpt.orElse(EXPECTED_FILE_SIZE);

                    logger.info("Размер файла: " + contentLength + " байт");
                    updateMessage("Скачивание обновления...");

                    if (contentLength > 0 && contentLength < MIN_VALID_SIZE) {
                        logger.severe("Слишком маленький размер файла: " + contentLength);
                        updateMessage("Ошибка: размер файла слишком мал");
                        return false;
                    }

                    // Скачиваем файл с отображением прогресса
                    try (InputStream in = response.body();
                         FileOutputStream out = new FileOutputStream(UPDATE_FILE)) {

                        logger.info("Начало загрузки файла...");
                        byte[] buffer = new byte[8192]; // Увеличим буфер для ускорения
                        final long fileSize = contentLength > 0 ? contentLength : EXPECTED_FILE_SIZE;
                        final long[] totalBytesRead = {0}; // Use an array to allow modification in lambda

                        for (int bytesRead; (bytesRead = in.read(buffer)) != -1; ) {
                            out.write(buffer, 0, bytesRead);
                            totalBytesRead[0] += bytesRead;

                            // Обновляем прогресс
                            double progress = (double) totalBytesRead[0] / fileSize;
                            updateProgress(progress, 1.0);
                            updateMessage("Скачивание обновления... " + formatFileSize(totalBytesRead[0]));
                            int percent = (int) (progress * 100);
                            Platform.runLater(() -> {
                                downloadPercent.set(percent);
                                percentLabel.setText(percent + "%");
                            });

                            // Логируем прогресс каждые 25%
                            if (percent % 25 == 0 && percent > 0) {
                                logger.info("Прогресс загрузки: " + percent + "% (" + formatFileSize(totalBytesRead[0]) + ")");
                            }
                        }

                        logger.info("Загрузка файла завершена. Всего загружено: " + formatFileSize(totalBytesRead[0]));

                        // Проверяем размер загруженного файла
                        if (totalBytesRead[0] < MIN_VALID_SIZE) {
                            logger.severe("Загруженный файл слишком мал: " + totalBytesRead[0] + " байт");
                            updateMessage("Ошибка: Загруженный файл слишком мал");
                            if (!updateFile.delete()) {
                                logger.warning("Не удалось удалить файл: " + updateFile.getAbsolutePath());
                            } // Удаляем некорректный файл
                            return false;
                        }
                    }

                    // Дополнительная проверка JAR-файла
                    updateMessage("Проверка файла обновления...");
                    logger.info("Выполняется проверка JAR-файла...");

                    if (!verifyJarFile(updateFile)) {
                        logger.severe("Файл обновления поврежден или не является JAR-файлом");
                        updateMessage("Ошибка: Файл обновления поврежден");
                        if (!updateFile.delete()) {
                            logger.warning("Не удалось удалить файл: " + updateFile.getAbsolutePath());
                        }
                        return false;
                    }

                    logger.info("Проверка JAR-файла успешна");
                    updateMessage("Загрузка завершена успешно!");
                    return true;
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Ошибка при загрузке файла: " + e.getMessage(), e);
                    updateMessage("Ошибка: " + e.getMessage());
                    // Удаляем частично загруженный файл при ошибке
                    if (updateFile.exists()) {
                        if (!updateFile.delete()) {
                            logger.warning("Не удалось удалить файл: " + updateFile.getAbsolutePath());
                        }
                    }
                    return false;
                }
            }
        };

        // Обработчики событий прогресса
        task.messageProperty().addListener((_, _, newValue) -> Platform.runLater(() -> statusLabel.setText(newValue)));

        task.progressProperty().addListener((_, _, newValue) -> Platform.runLater(() -> progressBar.setProgress(newValue.doubleValue())));

        // Обработчик завершения задачи
        task.setOnSucceeded(_ -> {
            Boolean result = task.getValue();
            Platform.runLater(() -> {
                progressStage.close();

                if (result) {
                    logger.info("Загрузка обновления успешно завершена");
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Обновление загружено");
                    successAlert.setHeaderText("Обновление успешно загружено");
                    successAlert.setContentText("Приложение будет перезапущено для применения обновления.");
                    successAlert.showAndWait();

                    try {
                        applyUpdate();
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Ошибка при применении обновления: " + e.getMessage(), e);
                        showErrorAlert("Ошибка при применении обновления", e.getMessage());
                    }
                } else {
                    logger.warning("Не удалось загрузить обновление");
                    showErrorAlert("Ошибка обновления", "Не удалось загрузить обновление. Проверьте подключение к интернету и попробуйте позже.");
                }
            });
        });

        task.setOnFailed(_ -> {
            Throwable exception = task.getException();
            logger.log(Level.SEVERE, "Ошибка при выполнении задачи загрузки", exception);
            Platform.runLater(() -> {
                progressStage.close();
                showErrorAlert("Ошибка обновления", exception != null ? exception.getMessage() : "Неизвестная ошибка");
            });
        });

        // Запускаем задачу в отдельном потоке
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // Форматирование размера файла
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " Б";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f КБ", size / 1024.0);
        } else {
            return String.format("%.2f МБ", size / (1024.0 * 1024));
        }
    }

    private static String getLatestVersion() {
        //logger.info("Запрос к URL для проверки версии: " + VERSION_URL);
        logger.info("Запрос к URL для проверки версии.");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERSION_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            logger.info("Код ответа для version.txt: " + statusCode);

            if (statusCode != 200) {
                logger.severe("Ошибка получения version.txt: HTTP " + statusCode);
                return null;
            }

            String responseBody = response.body();
            if (responseBody != null && !responseBody.isEmpty()) {
                // Берем первую строку и удаляем лишние пробелы
                String version = responseBody.lines().findFirst().orElse("").trim();
                logger.info("Получена версия: " + version);
                return version;
            } else {
                logger.warning("Файл version.txt пуст");
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при получении последней версии", e);
            return null;
        }
    }

    private static String getCurrentVersion() {
        try {
            HelloController controller = new HelloController();
            String version = controller.getApplicationVersion();
            logger.info("Текущая версия приложения: " + version);
            return version;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при получении текущей версии", e);
            return null;
        }
    }

    // Проверка, что файл действительно является корректным JAR
    private static boolean verifyJarFile(File jar) {
        if (!jar.exists()) {
            logger.warning("JAR-файл не существует: " + jar.getAbsolutePath());
            return false;
        }

        long fileSize = jar.length();
        if (fileSize < MIN_VALID_SIZE) {
            logger.warning("JAR-файл слишком мал: " + fileSize + " байт");
            return false;
        }

        logger.info("Проверка JAR-файла: " + jar.getAbsolutePath() + " (размер: " + formatFileSize(fileSize) + ")");

        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar)) {
            // Простая проверка - если можно открыть как JAR, значит формат правильный
            boolean hasEntries = jarFile.entries().hasMoreElements();
            logger.info("JAR-файл проверен, содержит записи: " + hasEntries);
            return hasEntries;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка при проверке JAR-файла", e);
            return false;
        }
    }

    private static void applyUpdate() {
        logger.info("Beginning update application...");

        try {
            // Check that downloaded update exists
            Path updateJar = Paths.get(UPDATE_FILE);
            if (!Files.exists(updateJar)) {
                throw new IOException("Update file not found: " + updateJar);
            }

            // Launch external updater and exit this application
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

            // Build command to launch external updater
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            command.add("-cp");
            command.add(JAR_NAME); // Use current jar for classpath
            command.add("org.forever.demo.ExternalUpdater");
            command.add("3"); // Wait 3 seconds before starting update

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(LOG_DIR, "updater_launch.log"));

            // Log full launch command
            logger.info("Launching external updater: " + String.join(" ", command));

            // Start updater process
            Process process = builder.start();
            builder.directory(new File("."));  // Ensure working directory is set

            // Exit current application to release file lock
            logger.info("External updater launched, exiting application...");
            System.exit(0);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error launching external updater", e);
            showErrorAlert("Update Error", "Failed to launch updater: " + e.getMessage());
        }
    }

    // Показать диалог с ошибкой
    private static void showErrorAlert(String header, String content) {
        logger.severe(header + ": " + content);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка обновления");
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}