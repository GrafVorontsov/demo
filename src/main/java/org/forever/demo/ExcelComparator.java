package org.forever.demo;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

public class ExcelComparator {
    /*
        public static Map<String, Map<String, List<List<String>>>> parseFiles(File[] files, ComparisonSettings settings) {
            Map<String, Map<String, List<List<String>>>> megaMap = new LinkedHashMap<>();

            for (File file : files) {
                try (FileInputStream fis = new FileInputStream(file);
                     Workbook workbook = file.getName().endsWith(".xlsx") ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis)) {

                    Sheet sheet = workbook.getSheetAt(0);

                    // Флаги для определения типа файла
                    boolean hasDebitCredit = false;
                    boolean hasPrihodRashod = false;
                    boolean hasDTKT = false;
                    boolean containsSaldo = false;

                    // Поиск заголовков в файле
                    for (Row row : sheet) {
                        boolean containsDebit = false;
                        boolean containsCredit = false;
                        boolean containsPrihod = false;
                        boolean containsRashod = false;
                        boolean containsDT = false;
                        boolean containsKT = false;

                        // Проверяем содержимое строки
                        for (Cell cell : row) {
                            String value = getCellValueAsString(cell).trim();

                            // Проверка на "Сальдо"
                            if (!value.isEmpty() && value.toLowerCase().contains("сальдо")) {
                                containsSaldo = true;
                                break;
                            }

                            // Проверка на заголовки
                            if (value.equalsIgnoreCase("Дебет")) containsDebit = true;
                            if (value.equalsIgnoreCase("Кредит")) containsCredit = true;
                            if (value.equalsIgnoreCase("Приход")) containsPrihod = true;
                            if (value.equalsIgnoreCase("Расход")) containsRashod = true;
                            if (value.equalsIgnoreCase("дт")) containsDT = true;
                            if (value.equalsIgnoreCase("кт")) containsKT = true;
                        }

                        // Если нашли "Сальдо", пропускаем строку
                        if (containsSaldo) {
                            containsSaldo = false;
                            continue;
                        }

                        // Проверяем, нашли ли мы заголовки Дебет/Кредит
                        if (containsDebit && containsCredit) {
                            hasDebitCredit = true;
                            break;
                        }

                        // Проверяем, нашли ли мы заголовки Приход/Расход
                        if (containsPrihod && containsRashod) {
                            hasPrihodRashod = true;
                            break;
                        }

                        // Проверяем, нашли ли мы заголовки Дт/Кт
                        if (containsDT && containsKT) {
                            hasDTKT = true;
                            break;
                        }
                    }

                    // Определяем, какой метод использовать для парсинга
                    Map<String, List<List<String>>> fileData;

                    if (hasDebitCredit) {
                        fileData = parseDebitCreditFile(workbook);
                    } else if (hasPrihodRashod) {
                        fileData = parsePrihodRashodFile(workbook);
                    } else if (hasDTKT) {
                        fileData = parseAiS(workbook);
                    } else {
                        // Если не нашли ни один из форматов, используем особый метод
                        // и выходим из текущего метода
                        settings.setComparePrihodRashod(true);
                        return parseFilesPrihodRashod(files);
                    }

                    // Добавляем данные в общий megaMap, только если там есть какие-то данные
                    if (!fileData.isEmpty()) {
                        megaMap.put(file.getName(), fileData);
                    }

                } catch (OldExcelFormatException e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Ошибка формата");
                        alert.setHeaderText(null);
                        alert.setContentText("Файл устаревшего формата Excel 5.0/7.0");
                        alert.showAndWait();
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Ошибка");
                        alert.setHeaderText(null);
                        alert.setContentText("Ошибка при работе с файлом: " + e.getMessage());
                        alert.showAndWait();
                    });
                }
            }

            return megaMap;
        }
    */
    public static Map<String, Map<String, List<List<String>>>> parseFiles(File[] files, ComparisonSettings settings) {
        // Если ни один из форматов не подходит, используем особый метод parseFilesPrihodRashod
        boolean needsSpecialParsing = false;
        Map<String, Map<String, List<List<String>>>> megaMap = new LinkedHashMap<>();

        for (File file : files) {
            try {
                // Первая попытка: используем WorkbookFactory для автоматического определения формата
                try (InputStream is = new FileInputStream(file);
                     Workbook workbook = WorkbookFactory.create(is)) {

                    boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                    if (requiresSpecialParsing) {
                        needsSpecialParsing = true;
                    }
                }
            } catch (Exception e) {
                // Проверяем сообщение об ошибке на наличие ключевых слов
                String errorMessage = e.getMessage() != null ? e.getMessage() : "";
                boolean isOffice2007XMLError = errorMessage.contains("Office 2007+ XML") ||
                        errorMessage.contains("OLE2") ||
                        errorMessage.contains("XSSF instead of HSSF");

                if (isOffice2007XMLError) {
                    // Если это ошибка формата, пробуем создать временный файл с правильным расширением
                    try {
                        // Создаем временный файл с расширением .xlsx
                        String baseName = FilenameUtils.getBaseName(file.getName());
                        File tempFile = new File(file.getParent(), baseName + ".xlsx");

                        // Копируем содержимое исходного файла
                        FileUtils.copyFile(file, tempFile);

                        // Пробуем открыть как XLSX
                        try (FileInputStream fis = new FileInputStream(tempFile);
                             Workbook workbook = new XSSFWorkbook(fis)) {

                            boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                            if (requiresSpecialParsing) {
                                needsSpecialParsing = true;
                            }

                            // Удаляем временный файл после успешного использования
                            tempFile.deleteOnExit();
                        }
                    } catch (Exception tempEx) {
                        // Если и этот подход не сработал, возможно, нужна конвертация
                        try {
                            ExcelConverter.ExcelFileInfo fileInfo = ExcelConverter.checkExcelVersion(file);
                            if (fileInfo.isConvertible()) {
                                File convertedFile = ExcelConverter.convertBiff5ToXlsx(file);
                                try (FileInputStream fis = new FileInputStream(convertedFile);
                                     Workbook workbook = new XSSFWorkbook(fis)) {

                                    boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                                    if (requiresSpecialParsing) {
                                        needsSpecialParsing = true;
                                    }
                                }
                            } else {
                                logAndShowError("Не удалось открыть файл: " + file.getName(), tempEx);
                            }
                        } catch (Exception convEx) {
                            logAndShowError("Ошибка при конвертации файла: " + file.getName(), convEx);
                        }
                    }
                } else if (errorMessage.contains("BIFF5") || errorMessage.contains("Excel 5.0/7.0")) {
                    // Это старый формат Excel, нужна конвертация
                    try {
                        File convertedFile = ExcelConverter.convertBiff5ToXlsx(file);
                        try (FileInputStream fis = new FileInputStream(convertedFile);
                             Workbook workbook = new XSSFWorkbook(fis)) {

                            processWorkbook(workbook, file, megaMap, settings);
                        }
                    } catch (Exception convEx) {
                        logAndShowError("Ошибка при конвертации файла: " + file.getName(), convEx);
                    }
                } else {
                    // Другая ошибка
                    logAndShowError("Ошибка при работе с файлом: " + file.getName(), e);
                }
            }
        }

        // Если обнаружено, что нужно использовать специальный парсинг
        if (needsSpecialParsing) {
            settings.setComparePrihodRashod(true);
            return parseFilesPrihodRashod(files);
        }

        return megaMap;
    }

    // Вспомогательный метод для обработки workbook
    private static boolean processWorkbook(Workbook workbook, File originalFile,
                                           Map<String, Map<String, List<List<String>>>> megaMap,
                                           ComparisonSettings settings) {
        Sheet sheet = workbook.getSheetAt(0);

        // Флаги для определения типа файла
        boolean hasDebitCredit = false;
        boolean hasPrihodRashod = false;
        boolean hasDTKT = false;
        boolean containsSaldo = false;

        // Поиск заголовков в файле
        for (Row row : sheet) {
            boolean containsDebit = false;
            boolean containsCredit = false;
            boolean containsPrihod = false;
            boolean containsRashod = false;
            boolean containsDT = false;
            boolean containsKT = false;

            // Проверяем содержимое строки
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();

                // Проверка на "Сальдо"
                if (!value.isEmpty() && value.toLowerCase().contains("сальдо")) {
                    containsSaldo = true;
                    break;
                }

                // Проверка на заголовки
                if (value.equalsIgnoreCase("Дебет")) containsDebit = true;
                if (value.equalsIgnoreCase("Кредит")) containsCredit = true;
                if (value.equalsIgnoreCase("Приход")) containsPrihod = true;
                if (value.equalsIgnoreCase("Расход")) containsRashod = true;
                if (value.equalsIgnoreCase("дт")) containsDT = true;
                if (value.equalsIgnoreCase("кт")) containsKT = true;
            }

            // Если нашли "Сальдо", пропускаем строку
            if (containsSaldo) {
                containsSaldo = false;
                continue;
            }

            // Проверяем, нашли ли мы заголовки
            if (containsDebit && containsCredit) {
                hasDebitCredit = true;
                break;
            }
            if (containsPrihod && containsRashod) {
                hasPrihodRashod = true;
                break;
            }
            if (containsDT && containsKT) {
                hasDTKT = true;
                break;
            }
        }

        // Определяем, какой метод использовать для парсинга
        Map<String, List<List<String>>> fileData;

        if (hasDebitCredit) {
            fileData = parseDebitCreditFile(workbook);
        } else if (hasPrihodRashod) {
            fileData = parsePrihodRashodFile(workbook);
        } else if (hasDTKT) {
            fileData = parseAiS(workbook);
        } else {
            // Если не нашли ни один из форматов, используем особый метод
            // и выходим из текущего метода
            settings.setComparePrihodRashod(true);
            // Отмечаем, что нужно использовать специальный парсинг
            return true;
        }

        // Добавляем данные в общий megaMap, только если там есть какие-то данные
        if (!fileData.isEmpty()) {
            megaMap.put(originalFile.getName(), fileData);
        }

        // Все обработано нормально, специальный парсинг не требуется
        return false;
    }

    // Вспомогательный метод для логирования и отображения ошибок
    private static void logAndShowError(String message, Exception e) {
        // Логирование
        Logger.getLogger(ExcelComparator.class.getName())
                .log(Level.SEVERE, message, e);

        // Отображение сообщения в UI
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message + "\n" + e.getMessage());
            alert.showAndWait();
        });
    }

    // Метод для обработки файлов Дебет/Кредит
    private static Map<String, List<List<String>>> parseDebitCreditFile(Workbook workbook) {
        Map<String, List<List<String>>> fileData = new HashMap<>();
        Sheet sheet = workbook.getSheetAt(0);
        int startRowIndex = -1;

        // Находим индекс строки, с которой начинаются данные
        for (Row row : sheet) {
            boolean containsDebit = false;
            boolean containsCredit = false;

            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();
                if (value.equalsIgnoreCase("Дебет")) containsDebit = true;
                if (value.equalsIgnoreCase("Кредит")) containsCredit = true;
            }

            if (containsDebit && containsCredit) {
                startRowIndex = row.getRowNum() + 1; // Строка после заголовков
                break;
            }
        }

        if (startRowIndex == -1) {
            return fileData; // Не нашли заголовки
        }

        // Регулярные выражения для проверки дат
        Pattern shortDatePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2}");
        Pattern longDatePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");

        // Считываем данные
        for (int i = startRowIndex; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);

            if (row == null || isRowEmpty(row) || containsSummary(row)) {
                break; // Останавливаемся на пустой строке или строке с "Итого"
            }

            String dateKey = null;
            List<String> rowData = new ArrayList<>();

            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell);

                if (dateKey == null) {  // Если ещё не нашли дату
                    // Проверка на дату
                    Matcher longMatcher = longDatePattern.matcher(cellValue);
                    if (longMatcher.find()) {
                        dateKey = longMatcher.group();
                        continue;  // Пропускаем добавление даты в rowData
                    }

                    Matcher shortMatcher = shortDatePattern.matcher(cellValue);
                    if (shortMatcher.find()) {
                        dateKey = convertToFullYear(shortMatcher.group());
                    }
                } else {  // Если дата уже найдена
                    // Проверяем, является ли значение числом
                    try {
                        Double.parseDouble(cellValue.replace(",", "."));  // Пробуем преобразовать в число
                        if (!cellValue.isEmpty()) {
                            rowData.add(cellValue);
                        }
                    } catch (NumberFormatException e) {
                        // Если не число - пропускаем
                    }
                }
            }

            if (dateKey != null && !rowData.isEmpty()) {
                fileData.putIfAbsent(dateKey, new ArrayList<>());
                fileData.get(dateKey).add(rowData);
            }
        }

        return fileData;
    }

    // Метод для обработки файлов Приход/Расход
    private static Map<String, List<List<String>>> parseAiS(Workbook workbook) {
        Map<String, List<List<String>>> fileData = new HashMap<>();
        Sheet sheet = workbook.getSheetAt(0);

        // Найдем индексы нужных колонок
        int dateCellIndex = -1;
        int prichodIndex = -1;
        int rashodIndex = -1;
        int headerRow = -1;

        for (Row row : sheet) {
            int headersFound = 0;
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();

                if (value.toLowerCase().contains("документ") && dateCellIndex == -1) {
                    dateCellIndex = cell.getColumnIndex();
                    headersFound++;
                }
                if (value.equalsIgnoreCase("дт") && prichodIndex == -1) {
                    prichodIndex = cell.getColumnIndex();
                    headersFound++;
                }
                if (value.equalsIgnoreCase("кт") && rashodIndex == -1) {
                    rashodIndex = cell.getColumnIndex();
                    headersFound++;
                }
            }

            // Если нашли все 3 заголовка в одной строке
            if (headersFound == 3) {
                headerRow = row.getRowNum();
                break;
            }
        }

        if (headerRow == -1) {
            return fileData; // Не нашли все нужные заголовки
        }

        // Регулярные выражения для проверки дат
        Pattern shortDatePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2}");
        Pattern longDatePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");

        // Обрабатываем данные
        for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);

            if (row == null) {
                continue;
            }

            // Получаем значения из нужных колонок
            Cell dateCell = row.getCell(dateCellIndex);
            Cell priceCell = row.getCell(prichodIndex);
            Cell sumCell = row.getCell(rashodIndex);

            // Получаем значение ячейки с датой как строку
            String dateCellValue = getCellValueAsString(dateCell);
            String dateKey = null;

            // Проверяем, соответствует ли значение формату даты
            Matcher longMatcher = longDatePattern.matcher(dateCellValue);
            if (longMatcher.find()) {
                dateKey = longMatcher.group();
            } else {
                Matcher shortMatcher = shortDatePattern.matcher(dateCellValue);
                if (shortMatcher.find()) {
                    dateKey = convertToFullYear(shortMatcher.group());
                }
            }

            // Если нашли дату и она соответствует формату
            if (dateKey != null) {
                // Получаем значения цены и суммы
                String priceValue = getCellValueAsString(priceCell);
                String sumValue = getCellValueAsString(sumCell);

                // Создаем список данных для текущей строки
                List<String> rowData = new ArrayList<>();

                // Добавляем значения в список, только если они числовые и не пустые
                if (isNumeric(priceValue) && !priceValue.isEmpty()) {
                    rowData.add(priceValue);
                }

                if (isNumeric(sumValue) && !sumValue.isEmpty()) {
                    rowData.add(sumValue);
                }

                // Добавляем данные в map только если список не пуст
                if (!rowData.isEmpty()) {
                    fileData.putIfAbsent(dateKey, new ArrayList<>());
                    fileData.get(dateKey).add(rowData);
                }
            }
        }

        return fileData;
    }

    private static Map<String, List<List<String>>> parsePrihodRashodFile(Workbook workbook) {
        Map<String, List<List<String>>> fileData = new HashMap<>();
        Sheet sheet = workbook.getSheetAt(0);

        // Найдем индексы нужных колонок
        int dateCellIndex = -1;
        int prichodIndex = -1;
        int rashodIndex = -1;
        int headerRow = -1;

        // Поиск колонок по заголовкам
        for (Row row : sheet) {
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();

                // Ищем заголовки колонок
                if (value.toLowerCase().contains("договор")) {
                    dateCellIndex = cell.getColumnIndex();
                }
                if (value.equalsIgnoreCase("приход")) {
                    prichodIndex = cell.getColumnIndex();
                }
                if (value.equalsIgnoreCase("расход")) {
                    rashodIndex = cell.getColumnIndex();
                }
            }

            // Если нашли все колонки
            if (dateCellIndex != -1 && prichodIndex != -1 && rashodIndex != -1) {
                headerRow = row.getRowNum();
                break;
            }
        }

        if (headerRow == -1) {
            return fileData; // Не нашли все нужные заголовки
        }

        // Регулярные выражения для проверки дат
        Pattern shortDatePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2}");
        Pattern longDatePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");

        // Обрабатываем данные
        for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);

            if (row == null) {
                continue;
            }

            // Получаем значения из нужных колонок
            Cell dateCell = row.getCell(dateCellIndex);
            Cell priceCell = row.getCell(prichodIndex);
            Cell sumCell = row.getCell(rashodIndex);

            // Получаем значение ячейки с датой как строку
            String dateCellValue = getCellValueAsString(dateCell);
            String dateKey = null;

            // Проверяем, соответствует ли значение формату даты
            Matcher longMatcher = longDatePattern.matcher(dateCellValue);
            if (longMatcher.find()) {
                dateKey = longMatcher.group();
            } else {
                Matcher shortMatcher = shortDatePattern.matcher(dateCellValue);
                if (shortMatcher.find()) {
                    dateKey = convertToFullYear(shortMatcher.group());
                }
            }

            // Если нашли дату и она соответствует формату
            if (dateKey != null) {
                // Получаем значения цены и суммы
                String priceValue = getCellValueAsString(priceCell);
                String sumValue = getCellValueAsString(sumCell);

                // Создаем список данных для текущей строки
                List<String> rowData = new ArrayList<>();

                // Добавляем значения в список, только если они числовые и не пустые
                if (isNumeric(priceValue) && !priceValue.isEmpty()) {
                    rowData.add(priceValue);
                }

                if (isNumeric(sumValue) && !sumValue.isEmpty()) {
                    rowData.add(sumValue);
                }

                // Добавляем данные в map только если список не пуст
                if (!rowData.isEmpty()) {
                    fileData.putIfAbsent(dateKey, new ArrayList<>());
                    fileData.get(dateKey).add(rowData);
                }
            }
        }

        return fileData;
    }

    // Вспомогательный метод для получения значения ячейки как строки
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
                    return sdf.format(cell.getDateCellValue());
                } else {
                    return String.valueOf(cell.getNumericCellValue()).trim();
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue()).trim();
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue()).trim();
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue().trim();
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    // Метод для проверки, пуста ли строка
    private static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }

        for (Cell cell : row) {
            if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    // Метод для проверки, содержит ли строка итоговые значения
    private static boolean containsSummary(Row row) {
        if (row == null) {
            return false;
        }

        for (Cell cell : row) {
            String value = getCellValueAsString(cell).trim().toLowerCase();
            if (value.contains("итого") || value.contains("всего")) {
                return true;
            }
        }

        return false;
    }

    // Метод для проверки, является ли строка числом
    private static boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }

        try {
            Double.parseDouble(str.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Метод для преобразования "dd.MM.yy" в "dd.MM.yyyy"
    private static String convertToFullYear(String date) {
        String[] parts = date.split("\\.");
        if (parts.length == 3) {
            String year = parts[2];
            if (year.length() == 2) {
                parts[2] = "20" + year;
            }
            return String.join(".", parts);
        }
        return date;
    }

    public static List<String> compareDataInMegaMap(Map<String, Map<String, List<List<String>>>> megaMap) {
        return compareDataInMegaMap(megaMap, false, false); // По умолчанию оба флага false
    }

    public static List<String> compareDataInMegaMap(
            Map<String, Map<String, List<List<String>>>> megaMap,
            boolean compareByAbsoluteValue,
            boolean comparePrihodRashod) {

        List<String> fileNames = new ArrayList<>(megaMap.keySet());
        List<String> differences = new ArrayList<>();

        if (comparePrihodRashod) { //Для сравнения приходно-расходных накладных

            for (Map.Entry<String, List<List<String>>> merged : megaMap.get("merged").entrySet()) {
                String mergedKey = merged.getKey();
                List<List<String>> mergedValue = merged.getValue();

                differences.add(String.format("    <span style='color: orange;'>Товар: %s</span>", mergedKey));

                // Создаем список для хранения всех значений value
                List<Double> values = new ArrayList<>();

                for (List<String> sum : mergedValue) {
                    // Предполагая, что сумма находится в первом элементе списка sum
                    // и может быть преобразована в double
                    double value = Double.parseDouble(sum.getFirst());
                    values.add(value);
                    differences.add(String.format("    <span style='color: blue;'>Сумма: %.2f</span>", value));
                }

                // Находим максимальное и минимальное значение
                double maxValue = Collections.max(values);
                double minValue = Collections.min(values);
                double diff = maxValue - minValue;
                differences.add(String.format("    <span style='color: red;'>Разница: %.2f</span>", diff));
            }
        } else { //Для актов сверки
            if (fileNames.size() < 2) {
                throw new IllegalArgumentException("Для сравнения необходимо минимум два файла.");
            }

            Map<String, List<List<String>>> file1Data = megaMap.get(fileNames.get(0));
            Map<String, List<List<String>>> file2Data = megaMap.get(fileNames.get(1));

            // Добавляем новую проверку совпадения ключей
            boolean keysMatchEnough = checkKeysMatchEnough(file1Data.keySet(), file2Data.keySet());

            if (!keysMatchEnough) {
                differences.add(String.format("    <span style='color: red;'>ВНИМАНИЕ: Наборы дат в файлах сильно различаются. Дальнейшее сравнение нецелесообразно.</span>"));
                // Можно добавить информацию о совпадающих и различающихся ключах
                differences.add(String.format("    <span style='color: blue;'>Файл 1 содержит: %d записей</span>", file1Data.size()));
                differences.add(String.format("    <span style='color: blue;'>Файл 2 содержит: %d записей</span>", file2Data.size()));

                Set<String> commonKeys = new HashSet<>(file1Data.keySet());
                commonKeys.retainAll(file2Data.keySet());
                differences.add(String.format("    <span style='color: orange;'>Количество совпадающих дат: %d</span>", commonKeys.size()));

                return differences; // Прекращаем дальнейшее сравнение
            }

            Set<String> allProducts = new HashSet<>();
            allProducts.addAll(file1Data.keySet());
            allProducts.addAll(file2Data.keySet());

            // Сортируем продукты для последовательного вывода
            List<String> sortedProducts = allProducts.stream().sorted().toList();

            for (String product : sortedProducts) {
                List<List<String>> file1Rows = file1Data.getOrDefault(product, new ArrayList<>());
                List<List<String>> file2Rows = file2Data.getOrDefault(product, new ArrayList<>());

                Map<String, Integer> file1Counts = buildValueCounts(file1Rows);
                Map<String, Integer> file2Counts = buildValueCounts(file2Rows);

                if (!compareMultisets(file1Counts, file2Counts)) {
                    differences.add("Различия найдены для даты: " + product);
                    differences.addAll(generateDifferenceDetails(file1Counts, file2Counts, compareByAbsoluteValue));
                }
            }
        }

        return differences;
    }

    // Создает мультимножество из списка строк
    private static Map<String, Integer> buildValueCounts(List<List<String>> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> row : rows) {
            for (String value : row) {
                counts.put(value, counts.getOrDefault(value, 0) + 1);
            }
        }
        return counts;
    }

    // Сравнивает два мультимножества
    private static boolean compareMultisets(Map<String, Integer> set1, Map<String, Integer> set2) {
        for (Map.Entry<String, Integer> entry : set1.entrySet()) {
            String value = entry.getKey();
            int count1 = entry.getValue();
            int count2 = set2.getOrDefault(value, 0);

            if (count1 != count2) {
                return false;
            }
        }

        for (Map.Entry<String, Integer> entry : set2.entrySet()) {
            String value = entry.getKey();
            if (!set1.containsKey(value)) {
                return false;
            }
        }

        return true;
    }

    // Новый метод для проверки совпадения ключей
    private static boolean checkKeysMatchEnough(Set<String> keys1, Set<String> keys2) {
        // Находим общие ключи
        Set<String> commonKeys = new HashSet<>(keys1);
        commonKeys.retainAll(keys2);

        int totalKeys = Math.max(keys1.size(), keys2.size());

        // Если общих ключей меньше 20% от общего количества, считаем несовпадение критическим
        double matchRatio = (double) commonKeys.size() / totalKeys;

        // Можно настроить этот порог (0.2 означает 20%)
        return matchRatio >= 0.2;
    }

    private static List<String> generateDifferenceDetails(Map<String, Integer> file1Counts, Map<String, Integer> file2Counts, boolean compareByAbsoluteValue) {
        List<String> details = new ArrayList<>();

        // Устанавливаем локаль с точкой как разделителем
        Locale.setDefault(Locale.US);

        // Используем AtomicReference для корректной работы с лямбдами
        AtomicReference<Double> file1Sum = new AtomicReference<>(0.0);
        AtomicReference<Double> file2Sum = new AtomicReference<>(0.0);

        // Метод для обработки чисел с точкой или запятой
        // Улучшенный метод парсинга числовых значений
        Function<String, Double> parseValue = value -> {
            try {
                if (value == null || value.trim().isEmpty()) {
                    return 0.0; // Возвращаем 0 для пустых значений
                }

                // Очищаем строку от лишних символов
                String cleanValue = value.trim()
                        .replace(" ", "") // Удаляем пробелы
                        .replace("\u00A0", "") // Неразрывный пробел
                        .replace(",", "."); // Заменяем запятую на точку

                // Попытка разбора числа
                return Double.parseDouble(cleanValue);
            } catch (NumberFormatException e) {
                // Выводим в лог для отладки
                System.err.println("Не удалось преобразовать значение: '" + value + "'");

                // Показываем диалог
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Ошибка формата");
                    alert.setHeaderText(null);
                    alert.setContentText("Некорректное числовое значение: '" + value + "'");
                    alert.showAndWait();
                });

                // Можно вернуть 0.0 вместо выброса исключения, если это приемлемо
                return 0.0;
            }
        };

        // Сравниваем элементы, которые есть в файле 1
        file1Counts.forEach((value, count1) -> {
            // Преобразуем значение в число
            double numericValue = parseValue.apply(value);
            if (compareByAbsoluteValue) {
                numericValue = Math.abs(numericValue); // Применяем модуль
            }

            int count2 = file2Counts.getOrDefault(value, 0);

            if (count1 > count2) {
                details.add(String.format("    <span style='color: blue;'>В файле 1: %.2f (количество: %d)</span>", numericValue, (count1 - count2)));
                file1Sum.set(file1Sum.get() + (numericValue * (count1 - count2)));  // Обновляем сумму
            }
        });

        // Сравниваем элементы, которые есть в файле 2
        file2Counts.forEach((value, count2) -> {
            // Преобразуем значение в число
            double numericValue = parseValue.apply(value);
            if (compareByAbsoluteValue) {
                numericValue = Math.abs(numericValue); // Применяем модуль
            }

            int count1 = file1Counts.getOrDefault(value, 0);

            if (count2 > count1) {
                details.add(String.format("    <span style='color: green;'>В файле 2: %.2f (количество: %d)</span>", numericValue, (count2 - count1)));
                file2Sum.set(file2Sum.get() + (numericValue * (count2 - count1)));  // Обновляем сумму
            }
        });

        // Итоговая сумма
        details.add("<span style='color: grey;'>  Итоговая сумма не совпавших значений:</span>");
        details.add("    В файле 1: " + String.format("<span style='color: blue;'>%.2f</span>", file1Sum.get()));
        details.add("    В файле 2: " + String.format("<span style='color: green;'>%.2f</span>", file2Sum.get()));

        double difference = Math.abs(file1Sum.get() - file2Sum.get());
        // Используем небольшой порог для сравнения
        if (difference < 0.000001) { // или можно использовать Math.ulp(1.0)
            details.add(String.format("    <span style='color: black;'>Разница: %.2f</span>", 0.00));
        } else {
            details.add(String.format("    <span style='color: red;'>Разница: %.2f</span>", difference));
        }

        return details;
    }

    //Приход-расход для сравнения товаров
    public static Map<String, Map<String, List<List<String>>>> parseFilesPrihodRashod(File[] files) {

        Map<String, Map<String, List<List<String>>>> megaMap = new LinkedHashMap<>();

        for (File file : files) {

            try (FileInputStream fis = new FileInputStream(file);
                 Workbook workbook = file.getName().endsWith(".xlsx") ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0);

                // Найдем индексы нужных колонок
                int productNameIndex = -1;
                int priceIndex = -1;
                int sumIndex = -1;
                int headerRow = -1;

                // Поиск колонок по заголовкам
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String value = getCellValueAsString1(cell).trim();

                        // Ищем заголовки колонок
                        if (value.equalsIgnoreCase("Товар")) {
                            productNameIndex = cell.getColumnIndex();

                        }
                        if (value.equalsIgnoreCase("Ціна без ПДВ")) {
                            priceIndex = cell.getColumnIndex();

                        }
                        if (value.equalsIgnoreCase("Сума без ПДВ")) {
                            sumIndex = cell.getColumnIndex();

                        }
                    }

                    // Если нашли все колонки
                    if (productNameIndex != -1 && priceIndex != -1 && sumIndex != -1) {
                        headerRow = row.getRowNum();

                        break;
                    }
                }

                if (headerRow == -1) {

                    continue;
                }

                Map<String, List<List<String>>> fileData = new HashMap<>();
                // Создаем map для хранения нормализованных ключей
                Map<String, String> normalizedKeys = new HashMap<>();

                // Начинаем читать со следующей строки после заголовков
                for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);

                    if (row == null) {

                        break;
                    }

                    // Получаем значения из нужных колонок
                    Cell productCell = row.getCell(productNameIndex);
                    Cell priceCell = row.getCell(priceIndex);
                    Cell sumCell = row.getCell(sumIndex);

                    if (productCell == null || priceCell == null || sumCell == null) {

                        break;
                    }

                    String productName = getCellValueAsString1(productCell).trim();
                    String priceStr = getCellValueAsString1(priceCell).trim();
                    String sumStr = getCellValueAsString1(sumCell).trim();

                    // Пропускаем пустые строки и строки с итогами
                    if (productName.isEmpty() || priceStr.isEmpty() || sumStr.isEmpty() ||
                            productName.toLowerCase().contains("итого") ||
                            productName.toLowerCase().contains("всего")) {
                        continue;
                    }

                    try {
                        double sum = Double.parseDouble(sumStr.replace(',', '.'));

                        // Округляем значения
                        double roundedSum = Math.round(sum * 100) / 100.0;

                        // Добавляем данные в fileData
                        String normalizedKey = normalize(productName); // используем метод normalize из предыдущего кода
                        if (!normalizedKeys.containsKey(normalizedKey)) {
                            normalizedKeys.put(normalizedKey, productName);
                        }

                        // Создаем новый список с данными
                        List<String> dataRow = List.of(
                                String.valueOf(roundedSum)  // Добавляем сумму как строку
                        );

                        // Добавляем данные в fileData
                        fileData.computeIfAbsent(normalizedKey, _ -> new ArrayList<>()).add(dataRow);

                    } catch (NumberFormatException _) {

                    }
                }

                megaMap.put(file.getName(), fileData);

            } catch (IOException _) {

            }
        }

        // Сравниваем данные между каждой парой файлов
        String key = "merged"; // здесь укажите нужный вам ключ

        // Добавляем mergedData в megaMap
        Map<String, Map<String, List<List<String>>>> megaMapka = new LinkedHashMap<>();
        Map<String, List<List<String>>> compareMegaMap = compareMegaMapData(megaMap, 2);

        megaMapka.put(key, compareMegaMap);

        return megaMapka;
    }

    public static Map<String, List<List<String>>> compareMegaMapData(
            Map<String, Map<String, List<List<String>>>> megaMap,
            int maxDistance) {

        // Получаем существующие ключи из megaMap
        Set<String> keys = megaMap.keySet();
        if (keys.size() != 2) {
            System.out.println("В megaMap должно быть ровно 2 ключа");

        }

        // Получаем два ключа из Set
        String[] keysArray = keys.toArray(new String[2]);
        String key1 = keysArray[0];
        String key2 = keysArray[1];

        // Получаем две Map для сравнения
        Map<String, List<List<String>>> fileData1 = megaMap.get(key1);
        Map<String, List<List<String>>> fileData2 = megaMap.get(key2);

        Map<String, List<List<String>>> mergedData = new HashMap<>();
        Set<String> processedKeys = new HashSet<>(); // Для отслеживания обработанных ключей

        // Обрабатываем похожие ключи
        for (Map.Entry<String, List<List<String>>> entry1 : fileData1.entrySet()) {
            String innerKey1 = entry1.getKey();
            List<List<String>> innerValue1 = entry1.getValue();

            String bestMatch = null;
            int bestScore = Integer.MAX_VALUE;

            for (Map.Entry<String, List<List<String>>> entry2 : fileData2.entrySet()) {
                String innerKey2 = entry2.getKey();

                int distance = levenshteinDistance(normalize(innerKey1), normalize(innerKey2));

                if (distance < bestScore) {
                    bestScore = distance;
                    bestMatch = innerKey2;
                }
            }

            if (bestScore <= maxDistance) {
                // Создаем Set для хранения уникальных значений
                Set<List<String>> uniqueValues = new HashSet<>();

                // Добавляем значения из первого файла
                uniqueValues.addAll(innerValue1);

                // Добавляем значения из второго файла
                uniqueValues.addAll(fileData2.get(bestMatch));

                // Преобразуем Set обратно в ArrayList
                List<List<String>> mergedList = new ArrayList<>(uniqueValues);

                // Добавляем в итоговую Map
                mergedData.put(bestMatch, mergedList);

                // Отмечаем оба ключа как обработанные
                processedKeys.add(innerKey1);
                processedKeys.add(bestMatch);
            } else {
                // Если не нашли похожего ключа, добавляем оригинальные данные
                mergedData.put(innerKey1, innerValue1);
                processedKeys.add(innerKey1);
            }
        }

// Добавляем оставшиеся необработанные данные из второго файла
        for (Map.Entry<String, List<List<String>>> entry2 : fileData2.entrySet()) {
            String key22 = entry2.getKey();
            if (!processedKeys.contains(key22)) {
                mergedData.put(key22, entry2.getValue());
            }
        }

        Map<String, List<List<String>>> finalMap = new HashMap<>();

        for (Map.Entry<String, List<List<String>>> merged : mergedData.entrySet()) {
            String mergedKey = merged.getKey();
            List<List<String>> mergedValue = merged.getValue();

            if (mergedValue.size() > 1) {
                finalMap.put(mergedKey, mergedValue);
            }
        }
        return finalMap;
    }

    public static String normalize(String str) {
        if (str == null) return "";

        // Базовая нормализация
        str = str.toLowerCase();
        str = str.replace('_', ' ');

        // Удаление запятых и точек
        str = str.replaceAll("[,.]", "");

        // Удаление кавычек
        str = str.replaceAll("\"", "");

        // Нормализация пробелов
        str = str.replaceAll("\\s+", " ").trim();

        // Специфические замены слов
        str = str.replace("ніжність", "ніжн");
        str = str.replace("інтенс зволоження", "інтенсзволож");
        str = str.replace("інтенс. зволоження", "інтенсзволож");

        // Унификация единиц измерения (считаем мл и г эквивалентными для 500)
        str = str.replaceAll("500\\s*г\\s+з\\s+розпилювачем", "500мл з розпилювачем");

        // Нормализация единиц измерения
        str = str.replaceAll("(\\d+)\\s*мл", "$1мл");
        str = str.replaceAll("(\\d+)\\s*г", "$1г");
        str = str.replaceAll("(\\d+)\\s*л", "$1л");
        str = str.replaceAll("(\\d+)\\s*шт", "$1шт");

        // Нормализация брендов
        str = str.replaceAll("nivea\\s+creme", "niveacreme");

        return str;
    }

    public static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost);
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private static String getCellValueAsString1(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (IllegalStateException e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }
}