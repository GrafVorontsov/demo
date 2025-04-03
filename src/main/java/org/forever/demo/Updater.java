package org.forever.demo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

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

    private static final String VERSION_URL = "https://drive.google.com/uc?export=download&id=1yzABoc0ZMWppL1cB9K5-A58g54vcJRPQ";
    private static final String UPDATE_URL = "https://drive.google.com/uc?export=download&id=1RFH8BQVxyAYdojJSQKCu_JE07ySF8NmM"; // Ссылка на JAR
    private static final String JAR_NAME = "demo-1.0-SNAPSHOT.jar"; // Название JAR-файла
    private static final String UPDATE_FILE = "update.jar"; // Временный файл для загрузки
    private static final long EXPECTED_FILE_SIZE = 30 * 1024 * 1024; // Примерно 30 МБ
    private static final long MIN_VALID_SIZE = 10 * 1024 * 1024; // Минимум 10 МБ для валидного JAR

    private static Stage progressStage;
    private static ProgressBar progressBar;
    private static Label statusLabel;
    private static Label percentLabel;

    private static final IntegerProperty downloadPercent = new SimpleIntegerProperty(0);

    // Метод для асинхронной проверки обновлений
    public static void checkForUpdatesAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                checkForUpdatesInternal();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Внутренний метод, который выполняет проверку
    private static void checkForUpdatesInternal() {
        try {
            String latestVersion = getLatestVersion();
            if (latestVersion == null) {
                return; // Тихий выход при ошибке
            }

            String currentVersion = getCurrentVersion();
            if (currentVersion == null) {
                return; // Тихий выход при ошибке
            }

            if (!latestVersion.equals(currentVersion)) {
                // Показываем диалог в потоке UI
                Platform.runLater(() -> showUpdateDialog(latestVersion));
            }
            // Если версии совпадают, ничего не выводим
        } catch (Exception e) {
            e.printStackTrace();
            // Тихий выход при ошибке
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
            // Пользователь согласился на обновление
            // Показываем анимированный диалог загрузки
            Platform.runLater(() -> showProgressDialog());

            // Запускаем скачивание в отдельном потоке
            downloadWithProgress(UPDATE_URL);
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

        Button cancelButton = new Button("Отмена");
        cancelButton.setOnAction(e -> {
            progressStage.close();
            // Очистка частично загруженного файла
            File updateFile = new File(UPDATE_FILE);
            if (updateFile.exists()) {
                updateFile.delete();
            }
        });

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(statusLabel, progressBar, percentLabel, cancelButton);

        Scene scene = new Scene(layout);
        progressStage.setScene(scene);
        progressStage.show();
    }

    // Загрузка с отображением прогресса
    private static void downloadWithProgress(String fileUrl) {
        Task<Boolean> task = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                File updateFile = new File(UPDATE_FILE);
                String downloadUrl = fileUrl; // Create a local copy to avoid modifying parameter

                // Удаляем предыдущий файл обновления, если существует
                if (updateFile.exists()) {
                    updateFile.delete();
                }

                try {
                    updateMessage("Подключение к серверу...");

                    // Первый запрос - получаем страницу с предупреждением
                    URL url = new URL(downloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();

                    // Если Google Drive требует подтверждение, получаем token
                    String confirmToken = null;
                    for (String header : conn.getHeaderFields().getOrDefault("Set-Cookie", new ArrayList<>())) {
                        if (header.contains("download_warning")) {
                            confirmToken = header.split(";")[0].split("=")[1];
                            break;
                        }
                    }

                    // Если есть токен, делаем повторный запрос с подтверждением
                    if (confirmToken != null) {
                        updateMessage("Получение доступа к файлу...");
                        downloadUrl += "&confirm=" + confirmToken;
                        url = new URL(downloadUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.connect();
                    }

                    // Проверяем размер файла, если он доступен
                    int contentLength = conn.getContentLength();
                    updateMessage("Скачивание обновления...");

                    if (contentLength > 0 && contentLength < MIN_VALID_SIZE) {
                        updateMessage("Ошибка: размер файла слишком мал");
                        return false;
                    }

                    // Скачиваем файл с отображением прогресса
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(UPDATE_FILE)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytesRead = 0;
                        long fileSize = contentLength > 0 ? contentLength : EXPECTED_FILE_SIZE;

                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;

                            // Обновляем прогресс
                            double progress = (double) totalBytesRead / fileSize;
                            updateProgress(progress, 1.0);
                            updateMessage("Скачивание обновления... " + formatFileSize(totalBytesRead));
                            int percent = (int) (progress * 100);
                            //updateValue(percent);
                        }

                        // Проверяем размер загруженного файла
                        if (totalBytesRead < MIN_VALID_SIZE) {
                            updateMessage("Ошибка: Загруженный файл слишком мал");
                            updateFile.delete(); // Удаляем некорректный файл
                            return false;
                        }
                    }

                    // Дополнительная проверка JAR-файла
                    updateMessage("Проверка файла обновления...");
                    if (!verifyJarFile(updateFile)) {
                        updateMessage("Ошибка: Файл обновления поврежден");
                        updateFile.delete();
                        return false;
                    }

                    updateMessage("Загрузка завершена успешно!");
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    updateMessage("Ошибка: " + e.getMessage());
                    // Удаляем частично загруженный файл при ошибке
                    if (updateFile.exists()) {
                        updateFile.delete();
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
                int percent = (int) (newValue.doubleValue() * 100);
                downloadPercent.set(percent);
                percentLabel.setText(percent + "%");
            });
        });

        task.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                Platform.runLater(() -> percentLabel.setText(newValue + "%"));
            }
        });

        // Обработчик завершения задачи
        task.setOnSucceeded(event -> {
            Boolean result = task.getValue();
            Platform.runLater(() -> {
                progressStage.close();

                if (result) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Обновление загружено");
                    successAlert.setHeaderText("Обновление успешно загружено");
                    successAlert.setContentText("Приложение будет перезапущено для применения обновления.");
                    successAlert.showAndWait();

                    try {
                        applyUpdate();
                    } catch (Exception e) {
                        e.printStackTrace();
                        showErrorAlert("Ошибка при применении обновления", e.getMessage());
                    }
                } else {
                    showErrorAlert("Ошибка обновления", "Не удалось загрузить обновление. Проверьте подключение к интернету и попробуйте позже.");
                }
            });
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
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
        try (Scanner scanner = new Scanner(new URL(VERSION_URL).openStream())) {
            return scanner.nextLine().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getCurrentVersion() {
        try {
            HelloController controller = new HelloController();
            return controller.getApplicationVersion();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Проверка, что файл действительно является корректным JAR
    private static boolean verifyJarFile(File jar) {
        if (!jar.exists() || jar.length() < MIN_VALID_SIZE) {
            return false;
        }

        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar)) {
            // Простая проверка - если можно открыть как JAR, значит формат правильный
            return jarFile.entries().hasMoreElements();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void applyUpdate() {
        try {
            Path currentJar = Paths.get(JAR_NAME);
            Path backupJar = Paths.get("backup_" + JAR_NAME);
            Path newJar = Paths.get(UPDATE_FILE);

            // Удаляем старый бэкап, если он есть
            Files.deleteIfExists(backupJar);

            // Переименовываем текущий JAR в backup
            Files.move(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING);

            // Перемещаем скачанный JAR в рабочую директорию
            Files.move(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);

            restartApplication();
        } catch (IOException e) {
            e.printStackTrace();

            // В случае ошибки восстанавливаем из бэкапа, если он существует
            try {
                Path backupJar = Paths.get("backup_" + JAR_NAME);
                Path currentJar = Paths.get(JAR_NAME);

                if (Files.exists(backupJar) && !Files.exists(currentJar)) {
                    Files.move(backupJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException restoreEx) {
                restoreEx.printStackTrace();
            }

            showErrorAlert("Ошибка обновления", "Не удалось применить обновление: " + e.getMessage());
        }
    }

    private static void restartApplication() throws IOException {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        File jarFile = new File(JAR_NAME);

        if (jarFile.exists()) {
            ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", jarFile.getAbsolutePath());
            builder.start();
            System.exit(0); // Завершаем текущий процесс
        } else {
            throw new IOException("Файл JAR не найден: " + jarFile.getAbsolutePath());
        }
    }

    // Показать диалог с ошибкой
    private static void showErrorAlert(String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка обновления");
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}