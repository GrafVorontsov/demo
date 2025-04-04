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

    static {
        // Инициализация логгера
        try {
            FileHandler fileHandler = new FileHandler("updater.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.info("Логгер успешно инициализирован");
        } catch (IOException e) {
            System.err.println("Ошибка при инициализации логгера: " + e.getMessage());
            // Добавляем обработчик для консольного вывода, чтобы логи не пропали
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(consoleHandler);
            logger.log(Level.SEVERE, "Не удалось создать файловый обработчик логов", e);
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
        cancelButton.setOnAction(e -> {
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
        task.messageProperty().addListener((obs, oldValue, newValue) -> {
            Platform.runLater(() -> statusLabel.setText(newValue));
        });

        task.progressProperty().addListener((obs, oldValue, newValue) -> {
            Platform.runLater(() -> {
                progressBar.setProgress(newValue.doubleValue());
            });
        });

        // Обработчик завершения задачи
        task.setOnSucceeded(event -> {
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

        task.setOnFailed(event -> {
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
        logger.info("Запрос к URL для проверки версии: " + VERSION_URL);

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
        logger.info("Начало применения обновления...");
        Path currentJar = Paths.get(JAR_NAME);
        Path backupJar = Paths.get("backup_" + JAR_NAME);
        Path newJar = Paths.get(UPDATE_FILE);

        try {
            // Проверяем существование файлов
            logger.info("Текущий JAR существует: " + Files.exists(currentJar));
            logger.info("Новый JAR существует: " + Files.exists(newJar));

            // Удаляем старый бэкап, если он есть
            if (Files.exists(backupJar)) {
                Files.delete(backupJar);
                logger.info("Удален старый бэкап: " + backupJar);
            }

            // Переименовываем текущий JAR в backup
            Files.move(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Текущий JAR перемещен в бэкап: " + backupJar);

            // Перемещаем скачанный JAR в рабочую директорию
            Files.move(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Новый JAR установлен: " + currentJar);

            // Проверяем, что файлы на месте после перемещения
            logger.info("После обновления - JAR существует: " + Files.exists(currentJar));
            logger.info("После обновления - Бэкап существует: " + Files.exists(backupJar));

            // Запускаем приложение с новым JAR
            restartApplication();
        } catch (IOException e) {
            // Используем более надежное логирование вместо e.printStackTrace()
            logger.log(Level.SEVERE, "Ошибка при применении обновления", e);

            // В случае ошибки восстанавливаем из бэкапа, если он существует
            try {
                if (Files.exists(backupJar) && !Files.exists(currentJar)) {
                    logger.info("Восстановление из бэкапа после ошибки...");
                    Files.move(backupJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Восстановление выполнено успешно");
                }
            } catch (IOException restoreEx) {
                // Используем более надежное логирование вместо restoreEx.printStackTrace()
                logger.log(Level.SEVERE, "Ошибка при восстановлении из бэкапа", restoreEx);
            }

            showErrorAlert("Ошибка обновления", "Не удалось применить обновление: " + e.getMessage());
        }
    }

private static void restartApplication() throws IOException {
    logger.info("Перезапуск приложения...");
    
    File jarFile = new File(JAR_NAME);
    if (!jarFile.exists()) {
        String errorMsg = "Файл JAR не найден: " + jarFile.getAbsolutePath();
        logger.severe(errorMsg);
        throw new IOException(errorMsg);
    }
    
    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    
    // Проверяем операционную систему для выбора правильной конфигурации
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    
    // Определяем путь к библиотекам JavaFX в зависимости от ОС
    String libPath = isWindows ? "libwindows" : "liblinux";
    
    // Проверяем существование директории с библиотеками
    File libDir = new File(libPath);
    if (!libDir.exists()) {
        logger.warning("Директория с библиотеками не найдена: " + libDir.getAbsolutePath());
        logger.info("Попытка использовать относительный путь");
    }
    
    // Проверяем существование исполняемого файла Java
    File javaFile = new File(javaBin);
    if (!javaFile.exists()) {
        logger.warning("Java исполняемый файл не найден по пути: " + javaBin);
        // Используем команду для запуска без полного пути
        javaBin = isWindows ? "javaw" : "java";
    }
    
    logger.info("Используем Java: " + javaBin);
    logger.info("Запускаем JAR: " + jarFile.getAbsolutePath());
    logger.info("Используем библиотеки JavaFX из: " + libPath);
    
    try {
        // Выводим информацию о текущей директории
        logger.info("Текущая директория: " + new File(".").getAbsolutePath());
        
        // Построение команды запуска с учетом JavaFX модулей
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("--module-path");
        command.add(libPath);
        command.add("--add-modules");
        command.add("javafx.controls,javafx.fxml");
        command.add("-jar");
        command.add(jarFile.getAbsolutePath());
        
        // Создаем ProcessBuilder с нашей командой
        ProcessBuilder builder = new ProcessBuilder(command);
        
        // Перенаправляем вывод для отладки
        builder.redirectErrorStream(true);
        File logFile = new File("restart.log");
        if (logFile.exists()) {
            if (!logFile.delete()) {
                logger.warning("Не удалось удалить файл: " + logFile.getAbsolutePath());
            }
        }
        builder.redirectOutput(logFile);
        
        // Логируем полную команду запуска
        logger.info("Команда запуска: " + String.join(" ", command));
        
        // Запускаем новый процесс
        Process process = builder.start();
        
        // Даем небольшую паузу, чтобы процесс успел стартовать
        Thread.sleep(1000);
        
        // Проверяем, что процесс запустился
        if (process.isAlive()) {
            logger.info("Новый процесс запущен успешно");
        } else {
            logger.warning("Новый процесс не запустился, код возврата: " + process.exitValue());
        }
        
        // Добавляем задержку перед выходом из приложения
        logger.info("Ожидание 3 секунды перед выходом...");
        Thread.sleep(3000);
        
        // Завершаем текущий процесс
        logger.info("Завершение текущего процесса...");
        System.exit(0);
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Ошибка при перезапуске приложения", e);
        throw new IOException("Ошибка при перезапуске: " + e.getMessage(), e);
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

/*
код метода restartApplication не годится вообще потому что
вот команда которая запускает приложение
в Linux
#!/bin/bash

java --module-path liblinux --add-modules javafx.controls,javafx.fxml -jar demo-1.0-SNAPSHOT.jar


в Windows
@echo off

REM Compare.bat

cd %~dp0

start javaw --module-path libwindows --add-modules javafx.controls,javafx.fxml -jar demo-1.0-SNAPSHOT.jar

exit
 */
