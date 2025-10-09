package org.forever.demo;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.util.Duration;
import org.forever.demo.ComparisonResult.DetailedComparisonData;
import org.forever.demo.ComparisonResult.MismatchInfo;
import javafx.scene.layout.GridPane;

import java.io.File;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.forever.demo.ExcelComparator.*;
import static org.forever.demo.ExcelConverter.convertFile;

public class HelloController {
    public Button compareButton;
    public Button printButton;
    public Button eyeButton;
    @FXML
    private Button clearButton;
    @FXML
    private TextArea fileDropArea1;
    @FXML
    private TextArea fileDropArea2;
    @FXML
    private TextFlow outputTextFlow;
    @FXML
    private CheckBox compareByAbsoluteValueCheckBox;

    @FXML
    private ImageView previewImage;

    @FXML
    private Label versionLabel;

    private File file1;
    private File file2;
    private boolean hasSameFilenames = false;
    private DetailedComparisonData detailedComparisonData = null;
    private Stage detailedViewStage = null;
    private String counterpartyName;

    // Style constants
    private static final String STYLE_ERROR = "-fx-fill: #d56949;";
    private static final String STYLE_SUCCESS = "-fx-fill: #58b462;";
    private static final String STYLE_INFO = "-fx-fill: #a9b7c6;";
    private static final String STYLE_WARNING = "-fx-fill: #dc8947; -fx-font-weight: bold; -fx-font-size: 16px;";
    private static final String STYLE_HIGHLIGHT = "-fx-fill: #3574f0;";
    private static final String STYLE_BOLD_INFO = "-fx-fill: #a9b7c6; -fx-font-weight: bold;";
    private static final String STYLE_BOLD_SUCCESS = "-fx-fill: #58b462; -fx-font-weight: bold; -fx-font-size: 16px;";

    @FXML
    public void initialize() {
        setupDragAndDrop(fileDropArea1, true);
        setupDragAndDrop(fileDropArea2, false);
        versionLabel.setText("Версия " + getApplicationVersion());

        // Добавляем иконки FontAwesome
        FontAwesomeIconView trashIcon = new FontAwesomeIconView(FontAwesomeIcon.TRASH);
        trashIcon.setSize("20px");
        trashIcon.setFill(Paint.valueOf("#E3E3E3"));
        clearButton.setGraphic(trashIcon);

        FontAwesomeIconView printIcon = new FontAwesomeIconView(FontAwesomeIcon.PRINT);
        printIcon.setSize("20px");
        printIcon.setFill(Paint.valueOf("#E3E3E3"));
        printButton.setGraphic(printIcon);

        FontAwesomeIconView eyeIcon = new FontAwesomeIconView(FontAwesomeIcon.EYE);
        eyeIcon.setSize("20px");
        eyeIcon.setFill(Paint.valueOf("#E3E3E3"));
        eyeButton.setGraphic(eyeIcon);

        eyeButton.setDisable(true);
        updateCompareButtonState();

        clearButton.setOnAction(_ -> {
            // Закрываем окно детального просмотра, если оно открыто
            if (detailedViewStage != null && detailedViewStage.isShowing()) {
                detailedViewStage.close();
                detailedViewStage = null; // Сбрасываем ссылку
            }
            // Очищаем оба TextArea
            fileDropArea1.clear();
            fileDropArea2.clear();

            // Очищаем TextFlow
            outputTextFlow.getChildren().clear();

            // Очищаем сохраненные файлы
            file1 = null;
            file2 = null;
            hasSameFilenames = false;

            // Обновляем текст в полях
            fileDropArea1.setText("Первый файл");
            fileDropArea2.setText("Второй файл");

            this.detailedComparisonData = null;
            eyeButton.setDisable(true);
            // Update button state after clearing
            updateCompareButtonState();
        });

        Tooltip tooltip = new Tooltip("Очистить");
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(clearButton, tooltip);

        Tooltip printTooltip = new Tooltip("Печать");
        printTooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(printButton, printTooltip);

        Tooltip eyeTooltip = new Tooltip("Просмотр");
        eyeTooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(eyeButton, eyeTooltip);
    }

    @FXML
    public void handleVersionClick(MouseEvent event) {
        showChangeLog();
    }
    private void showChangeLog() {
        outputTextFlow.getChildren().clear();

        // Create a header for the changelog
        Text changeLogHeaderText = new Text("История изменений\n\n");
        changeLogHeaderText.setFill(Color.web("#E3E3E3")); // You can customize this color
        changeLogHeaderText.setFont(Font.font("Verdana", FontWeight.BOLD, 18)); // Larger, bold font
        outputTextFlow.getChildren().add(changeLogHeaderText);

        for (ChangeEntry entry : ChangeLog.getChanges()) {
            Text versionText = new Text(entry.getVersion() + "\n");
            // Version text with the entry's color
            versionText.setFill(entry.getColor());
            versionText.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

            // Add more vertical spacing for version
            versionText.setLineSpacing(10);
            outputTextFlow.getChildren().add(versionText);

            for (String change : entry.getChanges()) {
                Text changeText = new Text("  • " + change + "\n");
                changeText.setFill(entry.getColor());
                changeText.setFont(Font.font("Verdana", FontWeight.NORMAL, 12));
                // Add more vertical spacing for change items
                changeText.setLineSpacing(8);
                outputTextFlow.getChildren().add(changeText);
            }
            // Add extra space between versions
            Text spacerText = new Text("\n");
            outputTextFlow.getChildren().add(spacerText);
        }
    }

    public String getApplicationVersion() {
        // Получение версии из свойств, манифеста или константы
        return "7.2.0";
    }

    // Method to update the compare button state
    private void updateCompareButtonState() {
        boolean filesReady = file1 != null && file2 != null && !hasSameFilenames;
        compareButton.setDisable(!filesReady);
    }

    @FXML
    private void handleCompareButtonAction() {
        if (file1 == null || file2 == null || hasSameFilenames) {
            showAlert(AlertType.ERROR, "Файлы не готовы!", null,
                    "Необходимо загрузить два различных файла для сравнения");
            outputTextFlow.getChildren().add(createStyledText(
                    "Необходимо загрузить два различных файла для сравнения", STYLE_INFO));
            return;
        }

        // Закрываем окно детального просмотра от предыдущего сравнения
        if (detailedViewStage != null && detailedViewStage.isShowing()) {
            detailedViewStage.close();
            detailedViewStage = null; // Сбрасываем ссылку
        }

        //Создаём индикатор прогресса
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(50, 50);

        Text statusText = new Text("Идёт обработка файлов...");
        statusText.setStyle(STYLE_INFO + " -fx-font-size: 14px;");

        VBox progressBox = new VBox(10, statusText, progressIndicator);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setStyle("-fx-padding: 20px;");

        outputTextFlow.getChildren().clear();
        outputTextFlow.getChildren().add(progressBox);

        final boolean compareByAbsoluteValue = compareByAbsoluteValueCheckBox.isSelected();

        // Надо сделать все методы парсинга и сравнения чтобы они выбрасывали исключения вместо показа Alert
        // А показывать Alert только в UI потоке
        CompletableFuture.runAsync(() -> {
            try {
                // >>> НАЧАЛО ИЗМЕНЕНИЙ
                // Ищем имя контрагента перед сравнением
                this.counterpartyName = ExcelComparator.findCounterpartyName(new File[]{file1, file2});
                // <<< КОНЕЦ ИЗМЕНЕНИЙ

                ExcelConverter.ExcelFileInfo fileInfo1 = ExcelConverter.checkExcelVersion(file1);
                ExcelConverter.ExcelFileInfo fileInfo2 = ExcelConverter.checkExcelVersion(file2);

                File convertedFile = convertFile(file1, file2);
                if (convertedFile != null) {
                    if (fileInfo1.isConvertible()) {
                        file1 = convertedFile;
                    } else if (fileInfo2.isConvertible()) {
                        file2 = convertedFile;
                    }
                }

                ComparisonSettings settings = new ComparisonSettings(false, false);
                Map<String, Map<String, List<List<String>>>> megaMap = parseFiles(new File[]{file1, file2}, settings);
                ComparisonResult result = compareDataInMegaMap(megaMap, compareByAbsoluteValue, settings.isComparePrihodRashod());

                Platform.runLater(() -> {
                    // Сохраняем детальные данные
                    this.detailedComparisonData = result.detailedData();
                    // Активируем или деактивируем кнопку просмотра
                    eyeButton.setDisable(this.detailedComparisonData == null);

                    List<String> differences = result.summaryLines();
                    if (differences.isEmpty()) {
                        outputTextFlow.getChildren().clear();
                        outputTextFlow.getChildren().add(createStyledText("Данные идентичны!", STYLE_HIGHLIGHT + " -fx-font-size: 14px;"));
                    } else {
                        displayDifferences(differences);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    // При ошибке также сбрасываем данные и кнопку
                    this.detailedComparisonData = null;
                    eyeButton.setDisable(true);
                    outputTextFlow.getChildren().clear();
                    outputTextFlow.getChildren().add(createStyledText("Ошибка: " + e.getMessage(), STYLE_ERROR));
                    e.printStackTrace(); // Для отладки
                });
            }
        });
    }

    private void setupDragAndDrop(TextArea dropArea, boolean isFirstFile) {
        dropArea.setOnDragOver(event -> {
            if (event.getGestureSource() != dropArea && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        dropArea.setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File droppedFile = db.getFiles().getFirst();

                // Update file reference
                if (isFirstFile) {
                    file1 = droppedFile;
                    dropArea.setText(droppedFile.getName());
                } else {
                    file2 = droppedFile;
                    dropArea.setText(droppedFile.getName());
                }

                // Check for same filenames
                if (file1 != null && file2 != null) {
                    hasSameFilenames = file1.getName().equals(file2.getName());
                    if (hasSameFilenames) {
                        showSameFileNameAlert();
                    }
                }

                // Update compare button state
                updateCompareButtonState();

                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // Helper method to show alert for same filename
    private void showSameFileNameAlert() {
        showAlert(AlertType.WARNING, "Одинаковые имена файлов", null,
                "Вы пытаетесь сравнить файлы с одинаковыми именами. Убедитесь, что это разные файлы.");
    }

    // Helper method to create styled text
    private Text createStyledText(String content, String style) {
        Text text = new Text(content + "\n");
        text.setStyle(style);
        return text;
    }

    // Helper method to show alerts
    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    //Показываем различия и подсвечиваем разными цветами
    private void displayDifferences(List<String> differences) {
        outputTextFlow.getChildren().clear();

        for (String difference : differences) {
            if (difference.contains("<span")) {
                // Обработка строки с HTML-тегами <span>
                outputTextFlow.getChildren().add(createColoredTextFromSpan(difference));
            } else if (difference.contains("Итоговая сумма несовпавших значений:")) {
                // Специальное оформление для строки "Итоговая сумма несовпавших значений:"
                outputTextFlow.getChildren().add(createStyledText(difference, STYLE_INFO + " -fx-font-size: 14px;"));
            } else if (difference.contains("Различия найдены для даты:") ||
                    difference.contains("Различия найдены для продукта:")) {
                // Обрабатываем строки с датой или продуктом
                displayKeyValueText(difference);
            } else {
                // Для обычных строк
                outputTextFlow.getChildren().add(new Text(difference + "\n"));
            }
        }
    }

    /**
     * Создает стилизованный Text объект из строки с HTML-тегами span
     * @param htmlText Строка с HTML-тегами span для стилизации
     * @return Объект Text со стилизацией
     */
    private Text createColoredTextFromSpan(String htmlText) {
        String styledText = extractTextFromHtml(htmlText);
        Text coloredText = new Text(styledText + "\n");

        // Применяем стили в зависимости от цвета в исходном HTML
        if (htmlText.contains("color: red")) {
            coloredText.setStyle(STYLE_ERROR + " -fx-font-weight: bold; -fx-font-size: 16px;");
        } else if (htmlText.contains("color: blue")) {
            coloredText.setStyle(STYLE_HIGHLIGHT);
        } else if (htmlText.contains("color: green")) {
            coloredText.setStyle(STYLE_SUCCESS);
        } else if (htmlText.contains("color: orange")) {
            coloredText.setStyle(STYLE_WARNING);
        } else if (htmlText.contains("color: grey")) {
            coloredText.setStyle(STYLE_INFO);
        } else if (htmlText.contains("color: black")) {
            coloredText.setStyle("-fx-fill: #8e6c6c; -fx-font-weight: bold;");
        }

        return coloredText;
    }

    // Extract text content from HTML span tags
    private String extractTextFromHtml(String htmlText) {
        return htmlText.replaceAll("<span style='color: red;'>", "")
                .replaceAll("<span style='color: blue;'>", "")
                .replaceAll("<span style='color: green;'>", "")
                .replaceAll("<span style='color: orange;'>", "") // Для "Итоговой суммы"
                .replaceAll("<span style='color: grey;'>", "")
                .replaceAll("<span style='color: black;'>", "")
                .replaceAll("</span>", ""); // Убираем теги
    }

    @FXML
    public void handleEyeButtonAction(ActionEvent event) {
        if (detailedComparisonData == null) {
            showAlert(AlertType.INFORMATION, "Нет данных", "Данные для просмотра отсутствуют", "Сначала выполните сравнение.");
            return;
        }

        try {
            // Если окно уже открыто, просто выводим его на передний план.
            // Это предотвращает открытие множества одинаковых окон.
            if (detailedViewStage != null && detailedViewStage.isShowing()) {
                detailedViewStage.toFront();
                return;
            }
            // Создаем новое окно и сохраняем ссылку на него
            this.detailedViewStage = new Stage();
            detailedViewStage.setTitle("Детальный просмотр расхождений");

            Stage newWindow = new Stage();
            newWindow.setTitle("Детальный просмотр расхождений");
            SplitPane splitPane = new SplitPane();

            Node view1 = createDetailedTableView(detailedComparisonData.file1Data(), detailedComparisonData.mismatches(), true);
            Node view2 = createDetailedTableView(detailedComparisonData.file2Data(), detailedComparisonData.mismatches(), false);

            // Передаем в ScrollPane чистый GridPane (view1 и view2)
            ScrollPane scrollPane1 = new ScrollPane(view1);
            ScrollPane scrollPane2 = new ScrollPane(view2);

            scrollPane1.setFitToWidth(true);
            scrollPane2.setFitToWidth(true);

            scrollPane1.getStyleClass().add("custom-scroll-pane");
            scrollPane2.getStyleClass().add("custom-scroll-pane");

            splitPane.getItems().addAll(scrollPane1, scrollPane2);
            splitPane.setDividerPositions(0.5);

            double initialWidth = 1240;
            double initialHeight = 620;

            Scene newWindowScene = new Scene(splitPane, initialWidth, initialHeight);

            // Используем ваш надежный способ загрузки CSS
            try {
                newWindowScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style/styles.css")).toExternalForm());
            } catch (Exception e) {
                System.err.println("Не удалось загрузить /style/styles.css.");
                e.printStackTrace();
            }

            detailedViewStage.setScene(newWindowScene);
            // Это сбросит нашу ссылку, чтобы мы знали, что окна больше нет.
            detailedViewStage.setOnCloseRequest(e -> {
                this.detailedViewStage = null;
            });

            // Показываем окно
            detailedViewStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Ошибка", "Не удалось открыть окно просмотра", "Произошла внутренняя ошибка: " + e.getMessage());
        }
    }

    /**
     * Создает таблицу (GridPane) для детального просмотра данных одного из файлов.
     *
     * @param fileData   Данные для отображения (ключ - дата, значение - строки с числами).
     * @param mismatches Информация о несовпадениях для подсветки.
     * @param isFile1    True, если это данные для файла 1 (для выбора цвета подсветки).
     * @return Узел (GridPane), содержащий отформатированную таблицу.
     */
    private Node createDetailedTableView(Map<String, List<List<String>>> fileData, Map<String, MismatchInfo> mismatches, boolean isFile1) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));

        // Ваши стили
        String highlightStyle = isFile1 ? STYLE_HIGHLIGHT : STYLE_SUCCESS;
        String highlightStyleFull = highlightStyle + " -fx-font-weight: bold;";
        String dateHeaderColor = "-fx-fill: #b3340f;";

        int rowIndex = 0;
        List<String> sortedKeys = fileData.keySet().stream().sorted().toList();

        Pattern datePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2,4}");

        for (String key : sortedKeys) {
            Text dateHeader = new Text(key);
            dateHeader.setStyle(dateHeaderColor + " -fx-font-weight: bold; -fx-font-size: 14px;");

            StackPane headerContainer = new StackPane(dateHeader);
            headerContainer.setPadding(new Insets(10, 0, 5, 0));
            headerContainer.setAlignment(Pos.CENTER_LEFT);
            headerContainer.setStyle("-fx-border-color: #515658; -fx-border-width: 0 0 1px 0;");

            // Увеличиваем columnspan для заголовка, чтобы он точно покрывал всю ширину
            grid.add(headerContainer, 0, rowIndex++, 5, 1);

            List<List<String>> allRowsForKey = fileData.get(key);
            if (allRowsForKey == null || allRowsForKey.isEmpty()) continue;

            MismatchInfo mismatchInfo = mismatches.get(key);
            List<List<String>> uniqueRows = (mismatchInfo != null)
                    ? (isFile1 ? mismatchInfo.uniqueRowsFile1() : mismatchInfo.uniqueRowsFile2())
                    : Collections.emptyList();
            Set<List<String>> uniqueRowsSet = new HashSet<>(uniqueRows);

            for (List<String> rowData : allRowsForKey) {
                boolean shouldHighlight = uniqueRowsSet.contains(rowData);

                // --- НАЧАЛО ИЗМЕНЕНИЙ: Проверяем, является ли строка итоговой/описательной ---
                // Условие: строка состоит из одной ячейки, и эта ячейка не является просто числом.
                if (rowData.size() == 1 && !isNumeric(rowData.getFirst())) {
                    String value = rowData.getFirst();
                    Text summaryText = new Text(value);
                    summaryText.setStyle(shouldHighlight ? highlightStyleFull : STYLE_INFO);
                    // Включаем перенос текста, чтобы он не выходил за пределы
                    summaryText.setWrappingWidth(550); // Ширина, в рамках которой текст будет переноситься

                    StackPane summaryContainer = new StackPane(summaryText);
                    summaryContainer.setAlignment(Pos.CENTER_LEFT);
                    summaryContainer.setPadding(new Insets(8, 8, 8, 8));
                    summaryContainer.setStyle("-fx-border-color: #515658; -fx-border-width: 0 0 1px 0;");

                    // Добавляем контейнер в сетку, указывая, что он должен занимать 5 колонок в ширину
                    grid.add(summaryContainer, 0, rowIndex, 5, 1);

                } else {
                    // --- СТАРАЯ ЛОГИКА для обычных строк с несколькими ячейками ---
                    for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                        String value = rowData.get(colIndex);
                        Text cellText = new Text(value);
                        cellText.setStyle(shouldHighlight ? highlightStyleFull : STYLE_INFO);

                        StackPane cellContainer = new StackPane(cellText);

                        double prefWidth;
                        boolean isShortContent = isNumeric(value) || datePattern.matcher(value).lookingAt();

                        if (isShortContent) {
                            prefWidth = 120;
                        } else {
                            prefWidth = 380;
                        }

                        cellContainer.setPrefWidth(prefWidth);
                        cellContainer.setAlignment(Pos.TOP_LEFT);
                        cellContainer.setStyle("-fx-border-color: #515658; -fx-border-width: 0 1px 1px 1px;");
                        cellContainer.setPadding(new Insets(5, 8, 5, 8));

                        grid.add(cellContainer, colIndex, rowIndex);
                    }
                }
                // --- КОНЕЦ ИЗМЕНЕНИЙ ---

                rowIndex++;
            }
            rowIndex++;
        }

        StackPane backgroundContainer = new StackPane(grid);
        backgroundContainer.setStyle("-fx-background-color: #1e1f22;");

        return backgroundContainer;
    }

    /**
     * Отображает текст в формате "Префикс: Значение" с разными стилями
     * @param keyValueText Строка в формате "Префикс: Значение"
     */
    private void displayKeyValueText(String keyValueText) {
        String[] parts = keyValueText.split(": ");
        if (parts.length == 2) {
            String prefix = parts[0] + ": ";
            String key = parts[1];

            // Префикс без стиля
            Text prefixText = new Text(prefix);
            prefixText.setStyle(STYLE_BOLD_INFO);

            // Ключ с выделением
            Text keyText = new Text(key);
            keyText.setStyle(STYLE_BOLD_SUCCESS);

            outputTextFlow.getChildren().addAll(prefixText, keyText, new Text("\n"));
        }
    }

    public void setPreviewImage(Image image) {
        if (previewImage != null) {
            previewImage.setImage(image);
        }
    }

    /**
     * Метод для показа предварительного просмотра перед печатью с разбивкой на страницы
     */
    private void showPrintPreview(Node contentToPrint) {
        try {
            // Получаем принтер
            Printer printer = Printer.getDefaultPrinter();
            if (printer == null) {
                showAlert(AlertType.ERROR, "Ошибка", "Принтер не найден",
                        "Не удалось найти доступный принтер. Пожалуйста, проверьте настройки принтера.");
                return;
            }

            // Создаем разметку страницы
            PageLayout pageLayout = printer.createPageLayout(
                    Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);

            // Создаем новое окно для предварительного просмотра
            Stage previewStage = new Stage();
            previewStage.setTitle("Предварительный просмотр печати");

            // Создаем контейнер для всех страниц
            VBox pagesContainer = new VBox(20);
            pagesContainer.setAlignment(Pos.CENTER);
            pagesContainer.setPadding(new Insets(20));
            pagesContainer.setStyle("-fx-background-color: #f0f0f0;");

            // Разделяем контент на страницы и добавляем их в контейнер
            List<Node> pages = paginateContent(contentToPrint, pageLayout);

            Label pagesInfo = new Label("Страниц: " + pages.size());
            pagesInfo.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            pagesContainer.getChildren().add(pagesInfo);

            // Добавляем каждую страницу в контейнер
            for (int i = 0; i < pages.size(); i++) {
                Node page = pages.get(i);

                // Создаем контейнер для страницы, чтобы имитировать лист бумаги
                StackPane pagePane = new StackPane(page);
                pagePane.setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");
                pagePane.setPrefWidth(pageLayout.getPrintableWidth() * 0.8); // Уменьшаем для видимости
                pagePane.setPrefHeight(pageLayout.getPrintableHeight() * 0.8);
                pagePane.setPadding(new Insets(20));

                // Добавляем номер страницы
                Label pageNumber = new Label("Страница " + (i + 1));
                pageNumber.setStyle("-fx-font-size: 12px;");
                VBox pageWithNumber = new VBox(10, pagePane, pageNumber);
                pageWithNumber.setAlignment(Pos.CENTER);

                pagesContainer.getChildren().add(pageWithNumber);
            }

            // Создаем скролл-панель для всех страниц
            ScrollPane scrollPane = new ScrollPane(pagesContainer);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(700);
            scrollPane.setPrefWidth(600);

            // Создаем кнопки управления
            Button printButton = new Button("Печать");
            Button eyeButton = new Button("Просмотр");
            Button cancelButton = new Button("Отмена");

            // Добавляем выбор режима печати
            CheckBox colorModeCheckBox = new CheckBox("Печать в цвете");
            colorModeCheckBox.setSelected(false); // По умолчанию Ч/Б режим

            // Обработчик для кнопки печати
            printButton.setOnAction(_ -> {
                previewStage.close();
                boolean colorMode = colorModeCheckBox.isSelected();
                printContent(contentToPrint, colorMode, pageLayout);
            });

            // Обработчик для кнопки печати
            eyeButton.setOnAction(_ -> {

            });

            // Обработчик для кнопки отмены
            cancelButton.setOnAction(_ -> previewStage.close());

            // Создаем панель с кнопками и чекбоксом
            HBox buttonBox = new HBox(20);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.setPadding(new Insets(15));
            buttonBox.getChildren().addAll(colorModeCheckBox, printButton, cancelButton);

            // Компонуем все в основной макет
            BorderPane mainLayout = new BorderPane();
            mainLayout.setCenter(scrollPane);
            mainLayout.setBottom(buttonBox);

            // Создаем сцену и отображаем окно
            Scene previewScene = new Scene(mainLayout, 700, 800);
            previewStage.setScene(previewScene);
            previewStage.initModality(Modality.APPLICATION_MODAL);
            previewStage.show();

        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Ошибка", "Ошибка предварительного просмотра",
                    "Не удалось создать окно предварительного просмотра: " + e.getMessage());
        }
    }

    /**
     * Разделяет контент на страницы, добавляя заголовок с именем контрагента.
     */
    private List<Node> paginateContent(Node originalContent, PageLayout pageLayout) {
        List<Node> pages = new ArrayList<>();
        if (!(originalContent instanceof TextFlow originalTextFlow) || originalTextFlow.getChildren().isEmpty()) {
            pages.add(originalContent);
            return pages;
        }

        double pagePrintableWidth = pageLayout.getPrintableWidth();
        double pagePrintableHeight = pageLayout.getPrintableHeight();
        double headerAndFooterHeight = 65;
        double contentHeightPerPage = pagePrintableHeight - headerAndFooterHeight;

        List<Node> sourceNodes = new ArrayList<>(originalTextFlow.getChildren());
        List<Node> currentPageNodes = new ArrayList<>();
        int pageCounter = 1;

        for (Node sourceNode : sourceNodes) {
            // Добавляем следующую строку на текущую страницу
            currentPageNodes.add(sourceNode);

            // Проверяем, не вышли ли мы за пределы
            double currentHeight = calculateFlowHeight(currentPageNodes, pagePrintableWidth);

            // Если вышли, и на странице была не только эта строка
            if (currentHeight > contentHeightPerPage && currentPageNodes.size() > 1) {
                // 1. Убираем последнюю добавленную строку (которая вызвала переполнение)
                Node overflowingNode = currentPageNodes.remove(currentPageNodes.size() - 1);

                // 2. Текущая страница (без последней строки) считается завершенной
                pages.add(createPagePane(pageCounter++, currentPageNodes, pageLayout));

                // 3. Начинаем новую страницу с той самой строки, которая не поместилась
                currentPageNodes = new ArrayList<>();
                currentPageNodes.add(overflowingNode);
            }
            // Если строка поместилась, просто продолжаем цикл
        }

        // Добавляем самую последнюю страницу, которая осталась после цикла
        if (!currentPageNodes.isEmpty()) {
            pages.add(createPagePane(pageCounter, currentPageNodes, pageLayout));
        }

        return pages;
    }

    /**
     * Находит индекс символа на указанной позиции Y
     */
    private int getCharIndexAtY(TextFlow textFlow, double y) {
        if (!textFlow.isManaged()) {
            new Scene(new Group(textFlow));
            textFlow.applyCss();
            textFlow.layout();
        }

        int totalChars = 0;
        for (Node node : textFlow.getChildren()) {
            if (node instanceof Text text) {
                Bounds bounds = text.getBoundsInParent();
                if (bounds.getMinY() >= y) {
                    return totalChars;
                }
                if (bounds.getMinY() < y && bounds.getMaxY() > y) {
                    String content = text.getText();
                    if (content.isEmpty()) continue;
                    double charHeight = bounds.getHeight() / content.length();
                    int charsBeforeY = (int) ((y - bounds.getMinY()) / charHeight);
                    return totalChars + Math.min(charsBeforeY, content.length());
                }
                totalChars += text.getText().length();
            }
        }
        return totalChars;
    }


    /**
     * Получает общую длину текста во всех Text узлах. (Остается без изменений, но нужен для нового paginateContent).
     */
    private int getFullTextLength(TextFlow textFlow) {
        int length = 0;
        for (Node node : textFlow.getChildren()) {
            if (node instanceof Text) {
                length += ((Text) node).getText().length();
            }
        }
        return length;
    }

    /**
     * Извлекает весь текст из TextFlow в единую строку.
     */
    private String getFullText(TextFlow textFlow) {
        StringBuilder sb = new StringBuilder();
        for (Node node : textFlow.getChildren()) {
            if (node instanceof Text text) {
                sb.append(text.getText());
            }
        }
        return sb.toString();
    }

    /**
     * Копирует часть текста из исходного TextFlow в целевой
     */
    private void copyTextInRange(TextFlow source, TextFlow target, int startIndex, int endIndex) {
        int currentIndex = 0;

        for (Node node : source.getChildren()) {
            if (node instanceof Text text) {
                String content = text.getText();

                // Определяем, входит ли текущий узел в нужный диапазон
                int nodeStartIndex = currentIndex;
                int nodeEndIndex = currentIndex + content.length();

                // Если текст узла входит в диапазон
                if (nodeEndIndex > startIndex && nodeStartIndex < endIndex) {
                    // Вычисляем начало и конец для вырезания из текущего узла
                    int textStartIndex = Math.max(0, startIndex - nodeStartIndex);
                    int textEndIndex = Math.min(content.length(), endIndex - nodeStartIndex);

                    // Создаем новый Text узел с нужной частью текста
                    String textPortion = content.substring(textStartIndex, textEndIndex);
                    Text newText = new Text(textPortion);
                    newText.setStyle(text.getStyle());

                    target.getChildren().add(newText);
                }

                currentIndex += content.length();
            }
        }
    }

    /**
     * Метод для печати содержимого с корректным масштабированием
     */
    private void printContent(Node content, boolean colorMode, PageLayout pageLayout) {
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) {
            showAlert(AlertType.ERROR, "Ошибка", "Принтер не найден",
                    "Не удалось найти доступный принтер. Пожалуйста, проверьте настройки принтера.");
            return;
        }
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        boolean proceed = job.showPrintDialog(content.getScene().getWindow());
        if (proceed) {
            try {
                List<Node> pages = paginateContent(content, pageLayout);
                List<Node> pagesToPrint = new ArrayList<>();

                // --- НАЧАЛО ИЗМЕНЕНИЙ: адаптация для BorderPane ---
                for (Node page : pages) {
                    if (!colorMode && page instanceof BorderPane pagePane) {
                        if (pagePane.getCenter() instanceof TextFlow textFlow) {
                            TextFlow bwTextFlow = createBwCopy(textFlow);
                            // Создаем копию страницы с Ч/Б текстом, но сохраняем заголовок
                            BorderPane bwPage = new BorderPane();
                            bwPage.setTop(pagePane.getTop()); // Используем существующий заголовок
                            bwPage.setCenter(bwTextFlow);
                            pagesToPrint.add(bwPage);
                        }
                    } else {
                        pagesToPrint.add(page);
                    }
                }
                // --- КОНЕЦ ИЗМЕНЕНИЙ ---

                JobSettings settings = job.getJobSettings();
                PageRange[] pageRanges = settings.getPageRanges();
                boolean success = true;

                if (pageRanges != null && pageRanges.length > 0) {
                    Set<Integer> pagesToPrintIndices = new HashSet<>();
                    for (PageRange range : pageRanges) {
                        for (int page = range.getStartPage(); page <= range.getEndPage(); page++) {
                            pagesToPrintIndices.add(page - 1);
                        }
                    }
                    List<Integer> sortedIndices = new ArrayList<>(pagesToPrintIndices);
                    Collections.sort(sortedIndices);
                    for (int index : sortedIndices) {
                        if (index >= 0 && index < pagesToPrint.size()) {
                            success = success && job.printPage(pageLayout, pagesToPrint.get(index));
                        }
                    }
                } else {
                    for (Node pageNode : pagesToPrint) {
                        success = success && job.printPage(pageLayout, pageNode);
                    }
                }

                if (success) {
                    job.endJob();
                    showAlert(AlertType.INFORMATION, "Информация", "Печать",
                            "Печать успешно отправлена на принтер.");
                } else {
                    showAlert(AlertType.ERROR, "Ошибка", "Ошибка печати",
                            "Произошла ошибка при отправке задания на печать. Пожалуйста, попробуйте снова.");
                }
            } catch (Exception e) {
                showAlert(AlertType.ERROR, "Ошибка", "Ошибка при подготовке печати",
                        "Произошла ошибка: " + e.getMessage());
            }
        }
    }

    /**
     * Вспомогательный метод: создает готовую панель страницы с заголовком и футером.
     */
    private BorderPane createPagePane(int pageNumber, List<Node> nodes, PageLayout pageLayout) {
        TextFlow content = new TextFlow();
        // ВАЖНО: Клонируем узлы, чтобы избежать ошибки "ребенок уже имеет родителя"
        for (Node node : nodes) {
            content.getChildren().add(cloneNode(node));
        }
        content.setPrefWidth(pageLayout.getPrintableWidth());

        Text headerText = new Text(this.counterpartyName);
        headerText.setFont(Font.font("Verdana", FontWeight.NORMAL, 24));
        StackPane headerPane = new StackPane(headerText);
        headerPane.setAlignment(Pos.CENTER);
        headerPane.setPadding(new Insets(5, 0, 15, 0));

        Label pageNumberLabel = new Label("Страница " + pageNumber);
        pageNumberLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        StackPane footerPane = new StackPane(pageNumberLabel);
        footerPane.setAlignment(Pos.CENTER_RIGHT);
        footerPane.setPadding(new Insets(5, 10, 5, 0));

        BorderPane pageLayoutPane = new BorderPane();
        pageLayoutPane.setPrefSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        pageLayoutPane.setTop(headerPane);
        pageLayoutPane.setCenter(content);
        pageLayoutPane.setBottom(footerPane);
        return pageLayoutPane;
    }

    /**
     * Вспомогательный метод: вычисляет реальную высоту TextFlow с заданным содержимым.
     */
    private double calculateFlowHeight(List<Node> nodes, double width) {
        TextFlow tempFlow = new TextFlow();
        // Клонируем узлы для "примерки"
        for (Node node : nodes) {
            tempFlow.getChildren().add(cloneNode(node));
        }
        tempFlow.setPrefWidth(width);
        // "Магия" для форсирования расчета размеров
        new Scene(new Group(tempFlow));
        tempFlow.applyCss();
        tempFlow.layout();
        return tempFlow.getBoundsInLocal().getHeight();
    }

    /**
     * Вспомогательный метод: создает клон узла (конкретно для Text).
     */
    private Node cloneNode(Node node) {
        if (node instanceof Text originalText) {
            Text clonedText = new Text(originalText.getText());
            clonedText.setStyle(originalText.getStyle());
            return clonedText;
        }
        return new Text(); // Заглушка на случай другого типа узла
    }

    /**
     * Обработчик нажатия кнопки печати
     */
    public void handlePrintButtonAction(ActionEvent event) {
        // Проверяем, есть ли содержимое для печати
        if (outputTextFlow == null || outputTextFlow.getChildren().isEmpty()) {
            showAlert(AlertType.INFORMATION, "Информация", "Нет данных для печати",
                    "Область вывода пуста. Пожалуйста, сначала выполните сравнение файлов.");
            return;
        }

        // Получаем принтер
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) {
            showAlert(AlertType.ERROR, "Ошибка", "Принтер не найден",
                    "Не удалось найти доступный принтер. Пожалуйста, проверьте настройки принтера.");
            return;
        }

        // Создаем разметку страницы
        PageLayout pageLayout = printer.createPageLayout(
                Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);

        // Показываем предварительный просмотр
        showPrintPreview(outputTextFlow);
    }

    /**
     * Преобразует цветные стили в оттенки серого
     */
    private String convertToBwStyle(String originalStyle) {
        if (originalStyle == null || originalStyle.isEmpty()) {
            return "-fx-fill: black;";
        }

        // Если стиль содержит цветовую информацию
        if (originalStyle.contains("-fx-fill:") || originalStyle.contains("-fx-text-fill:")) {
            // Находим важность текста по стилям
            boolean isBold = originalStyle.contains("-fx-font-weight: bold");
            boolean isLargerFont = originalStyle.contains("-fx-font-size: 16px") ||
                    originalStyle.contains("-fx-font-size: 14px");

            // Создаем соответствующий ч/б стиль
            StringBuilder bwStyle = new StringBuilder();

            // Решаем, насколько темным должен быть оттенок серого
            String greyShade;
            if (originalStyle.contains("-fx-fill: #d56949") || // красный
                    originalStyle.contains("-fx-fill: #3574f0") || // синий
                    originalStyle.contains("-fx-fill: #dc8947") || // оранжевый
                    originalStyle.contains("-fx-fill: #58b462")) { // зеленый
                // Важный текст - темно-серый или черный
                greyShade = "black";
            } else if (originalStyle.contains("-fx-fill: #a9b7c6") || // серый
                    originalStyle.contains("-fx-fill: #8e6c6c")) {  // темно-серый
                // Менее важный текст - средне-серый
                greyShade = "#555555";
            } else {
                // По умолчанию - темно-серый
                greyShade = "#333333";
            }

            bwStyle.append("-fx-fill: ").append(greyShade).append("; ");

            // Сохраняем форматирование
            if (isBold) {
                bwStyle.append("-fx-font-weight: bold; ");
            }

            if (isLargerFont) {
                bwStyle.append("-fx-font-size: 16px; ");
            }

            return bwStyle.toString();
        }

        // Если нет цветовой информации, возвращаем оригинальный стиль
        return originalStyle;
    }

    /**
     * Создает черно-белую копию TextFlow для печати
     */
    // Метод для создания Ч/Б копии TextFlow
    private TextFlow createBwCopy(TextFlow original) {
        TextFlow bwCopy = new TextFlow();

        for (Node child : original.getChildren()) {
            if (child instanceof Text originalText) {
                Text newText = new Text(originalText.getText());

                // Преобразуем цветные стили в оттенки серого
                String originalStyle = originalText.getStyle();
                String bwStyle = convertToBwStyle(originalStyle);
                newText.setStyle(bwStyle);

                bwCopy.getChildren().add(newText);
            }
        }

        return bwCopy;
    }
}