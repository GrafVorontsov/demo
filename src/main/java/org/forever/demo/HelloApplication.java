package org.forever.demo;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;

import java.io.IOException;
import java.util.Objects;

public class HelloApplication extends Application {
    private static final String MINIMUM_JAVA_VERSION = "23";

    @Override
    public void start(Stage stage) throws IOException {
        // Проверка версии Java
        if (!isJavaVersionCompatible()) {
            showIncompatibleVersionAlert();
            System.exit(1);
        }

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 600);
        // Get the controller to set up the default image
        HelloController controller = fxmlLoader.getController();

        // Load and set default preview image
        Image defaultPreview = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/logo.png")));
        controller.setPreviewImage(defaultPreview);

        // Подключаем CSS
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/dark-theme.css")).toExternalForm());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/styles.css")).toExternalForm());

        stage.getIcons().addAll(
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/16.png"))),
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/32.png"))),
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/48.png"))),
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/64.png"))),
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/128.png")))
        );

        stage.setTitle("Сравнивалка");
        stage.setScene(scene);
        stage.show();

        // Запускаем проверку обновлений в фоне ПОСЛЕ запуска приложения
        Updater.checkForUpdatesAsync();
    }

    private boolean isJavaVersionCompatible() {
        String javaVersion = System.getProperty("java.version");
        String majorVersion = getMajorJavaVersion(javaVersion);
        return Integer.parseInt(majorVersion) >= Integer.parseInt(MINIMUM_JAVA_VERSION);
    }

    private String getMajorJavaVersion(String javaVersion) {
        // Если версия в формате "17.0.1" или "1.8.0_281", берём первую цифру
        if (javaVersion.startsWith("1.")) {
            return javaVersion.split("\\.")[1]; // Возьмём вторую часть (например, "8" из "1.8")
        } else {
            return javaVersion.split("\\.")[0]; // Возьмём первую часть (например, "17")
        }
    }

    private void showIncompatibleVersionAlert() {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Неподдерживаемая версия Java");
        alert.setHeaderText("Минимальная версия Java: " + MINIMUM_JAVA_VERSION);
        alert.setContentText("Пожалуйста, обновите вашу Java до версии " + MINIMUM_JAVA_VERSION + " или выше.");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        // Сразу запускаем приложение без ожидания проверки обновлений
        launch(args);
    }
}