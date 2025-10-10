package org.forever.demo;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.forever.demo.ComparisonResult.DetailedComparisonData;
import org.forever.demo.ComparisonResult.MismatchInfo;

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

    public static Map<String, Map<String, List<List<String>>>> parseFiles(File[] files, ComparisonSettings settings) {
        Map<String, Map<String, List<List<String>>>> megaMap = new LinkedHashMap<>();
        Set<File> filesRequiringSpecialParsing = new HashSet<>();

        // Первый проход - обрабатываем все файлы обычным способом
        for (File file : files) {
            try {
                // Используем WorkbookFactory для автоматического определения формата
                try (InputStream is = new FileInputStream(file);
                     Workbook workbook = WorkbookFactory.create(is)) {

                    boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                    if (requiresSpecialParsing) {
                        filesRequiringSpecialParsing.add(file);
                        // Удаляем данные этого файла из megaMap, так как его будем обрабатывать особым образом
                        megaMap.remove(file.getName());
                    }
                }
            } catch (Exception e) {
                // Проверяем сообщение об ошибке на наличие ключевых слов
                String errorMessage = e.getMessage() != null ? e.getMessage() : "";
                boolean isOffice2007XMLError = errorMessage.contains("Office 2007+ XML") ||
                        errorMessage.contains("OLE2") ||
                        errorMessage.contains("XSSF instead of HSSF");

                if (isOffice2007XMLError) {
                    // Пытаемся обработать файл как XLSX
                    try {
                        // Создаем временную копию с правильным расширением
                        String baseName = FilenameUtils.getBaseName(file.getName());
                        // Проверяем, что длина префикса не меньше 3 символов
                        if (baseName.length() < 3) {
                            // Дополняем имя, чтобы оно соответствовало требованиям
                            baseName = baseName + "___"; // или baseName + System.currentTimeMillis();
                        }
                        File tempFile = File.createTempFile(baseName, ".xlsx");
                        tempFile.deleteOnExit();

                        // Копируем содержимое исходного файла
                        FileUtils.copyFile(file, tempFile);

                        // Пробуем открыть как XLSX
                        try (FileInputStream fis = new FileInputStream(tempFile);
                             Workbook workbook = new XSSFWorkbook(fis)) {

                            boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                            if (requiresSpecialParsing) {
                                filesRequiringSpecialParsing.add(file);
                                megaMap.remove(file.getName());
                            }
                        }
                    } catch (Exception tempEx) {
                        // Пробуем конвертировать старый формат
                        try {
                            ExcelConverter.ExcelFileInfo fileInfo = ExcelConverter.checkExcelVersion(file);
                            if (fileInfo.isConvertible()) {
                                File convertedFile = ExcelConverter.convertBiff5ToXlsx(file);
                                convertedFile.deleteOnExit();

                                try (FileInputStream fis = new FileInputStream(convertedFile);
                                     Workbook workbook = new XSSFWorkbook(fis)) {

                                    boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                                    if (requiresSpecialParsing) {
                                        filesRequiringSpecialParsing.add(file);
                                        megaMap.remove(file.getName());
                                    }
                                }
                            } else {
                                logAndShowError("Не удалось открыть файл: " + file.getName(), tempEx);
                                filesRequiringSpecialParsing.add(file); // Пометим как требующий специальной обработки
                            }
                        } catch (Exception convEx) {
                            logAndShowError("Ошибка при конвертации файла: " + file.getName(), convEx);
                            filesRequiringSpecialParsing.add(file); // Пометим как требующий специальной обработки
                        }
                    }
                } else if (errorMessage.contains("BIFF5") || errorMessage.contains("Excel 5.0/7.0")) {
                    // Конвертируем старый формат Excel
                    try {
                        File convertedFile = ExcelConverter.convertBiff5ToXlsx(file);
                        convertedFile.deleteOnExit();

                        try (FileInputStream fis = new FileInputStream(convertedFile);
                             Workbook workbook = new XSSFWorkbook(fis)) {

                            boolean requiresSpecialParsing = processWorkbook(workbook, file, megaMap, settings);
                            if (requiresSpecialParsing) {
                                filesRequiringSpecialParsing.add(file);
                                megaMap.remove(file.getName());
                            }
                        }
                    } catch (Exception convEx) {
                        logAndShowError("Ошибка при конвертации файла: " + file.getName(), convEx);
                        filesRequiringSpecialParsing.add(file); // Пометим как требующий специальной обработки
                    }
                } else {
                    // Другая ошибка
                    logAndShowError("Ошибка при работе с файлом: " + file.getName(), e);
                    filesRequiringSpecialParsing.add(file); // Пометим как требующий специальной обработки
                }
            }
        }

        // Если есть файлы, требующие специального парсинга, обрабатываем только их
        if (!filesRequiringSpecialParsing.isEmpty()) {
            settings.setComparePrihodRashod(true);

            // Преобразуем Set в массив для вызова parseFilesPrihodRashod
            File[] specialFiles = filesRequiringSpecialParsing.toArray(new File[0]);
            Map<String, Map<String, List<List<String>>>> specialParsedMap = parseFilesPrihodRashod(specialFiles);

            // Объединяем результаты обычного и специального парсинга
            megaMap.putAll(specialParsedMap);
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
        boolean hasPrihodRashodWithBalance = false;

        // Поиск заголовков в файле
        for (Row row : sheet) {
            boolean containsDebit = false;
            boolean containsCredit = false;
            boolean containsPrihod = false;
            boolean containsRashod = false;
            boolean containsDT = false;
            boolean containsKT = false;
            boolean containsNachOstatok = false; // ДЛЯ РАЗЛИЧЕНИЯ ТИПОВ

            // Проверяем содержимое строки
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();

                // Проверка на заголовки
                if (value.equalsIgnoreCase("Дебет")) containsDebit = true;
                if (value.equalsIgnoreCase("Кредит")) containsCredit = true;
                if (value.equalsIgnoreCase("Приход")) containsPrihod = true;
                if (value.equalsIgnoreCase("Расход")) containsRashod = true;
                if (value.equalsIgnoreCase("дт")) containsDT = true;
                if (value.equalsIgnoreCase("кт")) containsKT = true;
                if (value.contains("нач. остаток") || value.contains("кон. остаток")) {
                    containsNachOstatok = true;
                }
            }

            // Проверяем, нашли ли мы заголовки
            if (containsDebit && containsCredit) {
                hasDebitCredit = true;
                break;
            }
            if (containsPrihod && containsRashod) {
                if (containsNachOstatok) {
                    hasPrihodRashodWithBalance = true; // Старый формат с остатками
                } else {
                    hasPrihodRashod = true; // Новый формат (как Дебет/Кредит)
                }
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
            // НОВЫЙ файл обрабатываем как Дебет/Кредит
            fileData = parsePrihodRashodAsDebitCredit(workbook);
        } else if (hasPrihodRashodWithBalance) {
            // Старый файл с остатками
            fileData = parsePrihodRashodFile(workbook);
        } else if (hasDTKT) {
            fileData = parseAiS(workbook);
        } else {
            return true; // Требуется специальный парсинг
        }

        // Добавляем данные в общий megaMap, только если там есть какие-то данные
        if (!fileData.isEmpty()) {
            // Используем уникальный ключ для каждого файла, добавляя временную метку,
            // чтобы избежать коллизий при одинаковых именах файлов
            String uniqueKey = originalFile.getName();
            megaMap.put(uniqueKey, fileData);
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
        Map<String, List<List<String>>> fileData = new LinkedHashMap<>();
        Sheet sheet = workbook.getSheetAt(0);
        int startRowIndex = -1;
        int saldoColumnIndex = -1;

        // Ищем строку с заголовками "Дебет" и "Кредит"
        for (Row row : sheet) {
            if (row == null) continue;
            boolean containsDebit = false;
            boolean containsCredit = false;
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim().toLowerCase();
                if (value.equals("дебет")) containsDebit = true;
                if (value.equals("кредит")) containsCredit = true;
                if (value.equals("сальдо")) saldoColumnIndex = cell.getColumnIndex();
            }
            if (containsDebit && containsCredit) {
                startRowIndex = row.getRowNum() + 1;
                break;
            }
        }

        if (startRowIndex == -1) {
            return fileData;
        }

        Pattern datePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2,4}");

        for (int i = startRowIndex; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row) || containsSummary(row)) {
                break;
            }

            List<String> rowData = new ArrayList<>();
            String dateKey = null;

            // Собираем все непустые ячейки, игнорируя только "Сальдо"
            for (Cell cell : row) {
                if (cell.getColumnIndex() == saldoColumnIndex) {
                    continue;
                }
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    rowData.add(value);
                }
            }

            if (rowData.isEmpty()) continue;

            // Если первый элемент - это порядковый номер, и есть другие данные, удаляем его.
            if (rowData.size() > 1 && isSequentialNumber(rowData.getFirst())) {
                rowData.removeFirst();
            }

            // Ищем дату в оставшихся данных
            for (String cellValue : rowData) {
                Matcher m = datePattern.matcher(cellValue);
                if (m.find()) {
                    dateKey = convertToFullYear(m.group());
                    break;
                }
            }

            // Добавляем строку, только если в ней была найдена дата
            if (dateKey != null) {
                fileData.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(rowData);
            }
        }
        return fileData;
    }

    private static Map<String, List<List<String>>> parseAiS(Workbook workbook) {
        Map<String, List<List<String>>> fileData = new HashMap<>();
        Sheet sheet = workbook.getSheetAt(0);

        // Ищем строку с заголовками "Дт" и "Кт"
        int headerRow = -1;
        for (Row row : sheet) {
            if (row == null) continue;
            boolean hasDt = false;
            boolean hasKt = false;
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim().toLowerCase();
                if (value.equals("дт")) hasDt = true;
                if (value.equals("кт")) hasKt = true;
            }
            if (hasDt && hasKt) {
                headerRow = row.getRowNum();
                break;
            }
        }

        if (headerRow == -1) {
            return fileData; // Если нет Дт/Кт, это не наш файл
        }

        Pattern datePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2,4}");

        for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            // Убираем проверку на containsSummary, так как она мешает "Сальдо на..."
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            // Специальная проверка на "Разом" или "Обороты" прямо здесь
            boolean isSummaryRow = false;
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim().toLowerCase();
                if (value.contains("разом") || value.contains("оборот")) {
                    isSummaryRow = true;
                    break;
                }
            }
            if (isSummaryRow) break; // Если это итоговая строка, прерываем парсинг


            List<String> rowData = new ArrayList<>();
            String dateKey = null;

            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    // Фильтруем нули ПРЯМО ЗДЕСЬ
                    if (isNumeric(value)) {
                        if (isNonZeroNumeric(value)) {
                            rowData.add(value);
                        }
                    } else {
                        rowData.add(value);
                    }
                }
            }

            if (rowData.isEmpty()) continue; // Пропускаем строки, которые стали пустыми после фильтрации нулей

            // Ищем дату
            for (String cellValue : rowData) {
                Matcher m = datePattern.matcher(cellValue);
                if (m.find()) {
                    dateKey = convertToFullYear(m.group());
                    break;
                }
            }

            if (rowData.getFirst().toLowerCase().contains("сальдо на")) {
                // Пытаемся извлечь дату из строки "Сальдо на..."
                Matcher m = datePattern.matcher(rowData.getFirst());
                if (m.find()) {
                    dateKey = convertToFullYear(m.group());
                }
            }

            if (dateKey != null) {
                fileData.computeIfAbsent(dateKey, _ -> new ArrayList<>()).add(rowData);
            }
        }

        return fileData;
    }

    private static Map<String, List<List<String>>> parsePrihodRashodFile(Workbook workbook) {
        Map<String, List<List<String>>> fileData = new HashMap<>();
        Sheet sheet = workbook.getSheetAt(0);

        // --- НАХОДИМ ВСЕ НУЖНЫЕ И НЕНУЖНЫЕ КОЛОНКИ ---
        int nachOstatokIndex = -1; // Индекс колонки "нач. остаток"
        int konOstatokIndex = -1;  // Индекс колонки "кон. остаток"
        int prichodIndex = -1;     // Индекс "приход" для проверки
        int rashodIndex = -1;      // Индекс "расход" для проверки
        int dateIndex = -1;        // Индекс колонки с датой
        int headerRow = -1;

        // Ищем строку с заголовками
        for (Row row : sheet) {
            if (row == null) continue;
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim().toLowerCase();
                if (value.contains("нач. остаток")) nachOstatokIndex = cell.getColumnIndex();
                if (value.contains("кон. остаток")) konOstatokIndex = cell.getColumnIndex();
                if (value.equals("приход")) prichodIndex = cell.getColumnIndex();
                if (value.equals("расход")) rashodIndex = cell.getColumnIndex();
                // Дату ищем в колонке под "Период" или "Договор"
                if (value.contains("период") || value.contains("договор")) dateIndex = cell.getColumnIndex();
            }
            // Нам достаточно найти хотя бы приход/расход, чтобы понять, где заголовки
            if (prichodIndex != -1 && rashodIndex != -1 && dateIndex != -1) {
                headerRow = row.getRowNum();
                break;
            }
        }

        if (headerRow == -1) {
            return fileData; // Не нашли ключевые заголовки
        }

        Pattern datePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");

        for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row) || containsSummary(row)) {
                continue;
            }

            // Проверяем, что это строка с операцией (есть число в приходе/расходе)
            String prihodValue = getCellValueAsString(row.getCell(prichodIndex));
            String rashodValue = getCellValueAsString(row.getCell(rashodIndex));

            if (isNumeric(prihodValue) || isNumeric(rashodValue)) {

                // Если да, ищем дату в ключевой колонке
                String dateValue = getCellValueAsString(row.getCell(dateIndex));
                Matcher m = datePattern.matcher(dateValue);

                if (m.find()) {
                    String dateKey = m.group();
                    List<String> rowData = new ArrayList<>();

                    // --- ВОТ ОНО, РЕШЕНИЕ! ---
                    // Собираем все ячейки, КРОМЕ тех, что в ненужных колонках.
                    for (Cell cell : row) {
                        int currentColumnIndex = cell.getColumnIndex();
                        if (currentColumnIndex == nachOstatokIndex || currentColumnIndex == konOstatokIndex) {
                            continue; // Пропускаем колонки "нач. остаток" и "кон. остаток"
                        }
                        String cellValue = getCellValueAsString(cell).trim();
                        if (!cellValue.isEmpty()) {
                            rowData.add(cellValue);
                        }
                    }

                    if (!rowData.isEmpty()) {
                        fileData.computeIfAbsent(dateKey, _ -> new ArrayList<>()).add(rowData);
                    }
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

        // Список слов, которые всегда означают конец таблицы
        List<String> absoluteStopWords = List.of(
                "итого", "ітого",
                "всего", "всього",
                "оборот",
                "разом"  // <-- "Разом" - это всегда конец.
        );

        // Список слов-триггеров, которые делают "сальдо" стоп-словом.
        List<String> saldoContextWords = List.of(
                "конец", "кінцеве", "кінець"
        );

        for (Cell cell : row) {
            // Приводим к нижнему регистру для регистронезависимого сравнения
            String value = getCellValueAsString(cell).trim().toLowerCase();
            if (value.isEmpty()) {
                continue;
            }

            // 1. Проверка на абсолютные стоп-слова ("разом", "итого" и т.д.)
            for (String word : absoluteStopWords) {
                if (value.contains(word)) {
                    return true;
                }
            }

            // 2. Умная проверка на "Сальдо". Сработает только для "сальдо кінцеве" и т.п.
            // И НЕ сработает для "сальдо на початок".
            if (value.contains("сальдо")) {
                for (String context : saldoContextWords) {
                    if (value.contains(context)) {
                        // Нашли, например, "сальдо кінцеве". Это точно конец.
                        return true;
                    }
                }
                // Если дошли сюда, значит, это было "сальдо на...", и мы НЕ останавливаемся.
            }
        }

        // Если ни одно из правил не сработало, то это обычная строка данных.
        return false;
    }

    // Метод для проверки, является ли строка числом
    static boolean isNumeric(String str) {
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

    /**
     * Внутренний record для возврата результата из generateDifferenceDetails.
     *
     * @param summaryStrings Строки для сводки в главном окне.
     * @param mismatchInfo   Информация об уникальных значениях для подсветки.
     */
    private record DifferenceDetailsResult(List<String> summaryStrings, ComparisonResult.MismatchInfo mismatchInfo) {
    }

    public static ComparisonResult compareDataInMegaMap(
            Map<String, Map<String, List<List<String>>>> megaMap,
            boolean compareByAbsoluteValue,
            boolean comparePrihodRashod) {

        List<String> fileNames = new ArrayList<>(megaMap.keySet());
        List<String> differencesSummary = new ArrayList<>();

        if (comparePrihodRashod) { // Для сравнения приходно-расходных накладных

            //адаптируем его под новый формат вывода
            for (Map.Entry<String, List<List<String>>> merged : megaMap.get("merged").entrySet()) {
                String mergedKey = merged.getKey();
                List<List<String>> mergedValue = merged.getValue();

                differencesSummary.add(String.format("    <span style='color: orange;'>Товар: %s</span>", mergedKey));

                // Создаем список для хранения всех значений value
                List<Double> values = new ArrayList<>();

                for (List<String> sum : mergedValue) {
                    // Предполагая, что сумма находится в первом элементе списка sum
                    // и может быть преобразована в double
                    try {
                        double value = Double.parseDouble(sum.getFirst().replace(',', '.'));
                        values.add(value);
                        differencesSummary.add(String.format("    <span style='color: blue;'>Сумма: %.2f</span>", value));
                    } catch (NumberFormatException e) {
                        System.err.println("Не удалось распарсить сумму для товара: " + mergedKey + ", значение: " + sum.getFirst());
                    }
                }

                if (values.size() > 1) {
                    // Находим максимальное и минимальное значение
                    double maxValue = Collections.max(values);
                    double minValue = Collections.min(values);
                    double diff = maxValue - minValue;
                    differencesSummary.add(String.format("    <span style='color: red;'>Разница: %.2f</span>", diff));
                }
            }

            // ВАЖНО: return должен быть *после* цикла, а не внутри него.
            // Для этого режима у нас нет детальных данных, поэтому передаем null.
            return new ComparisonResult(differencesSummary, null);

        } else { //Для актов сверки
            if (fileNames.size() < 2) {
                throw new IllegalArgumentException("Для сравнения необходимо минимум два файла.");
            }

            Map<String, List<List<String>>> file1Data = megaMap.get(fileNames.get(0));
            Map<String, List<List<String>>> file2Data = megaMap.get(fileNames.get(1));

            Map<String, MismatchInfo> mismatches = new HashMap<>();

            // Проверка совпадения ключей
            boolean keysMatchEnough = checkKeysMatchEnough(file1Data.keySet(), file2Data.keySet());

            if (!keysMatchEnough) {
                //differencesSummary.add(String.format("    <span style='color: red;'>ВНИМАНИЕ: Наборы дат в файлах сильно различаются. Дальнейшее сравнение нецелесообразно.</span>"));
                differencesSummary.add("    <span style='color: red;'>ВНИМАНИЕ: Наборы дат в файлах сильно различаются. Дальнейшее сравнение нецелесообразно.</span>");
                differencesSummary.add(String.format("    <span style='color: blue;'>Файл 1 содержит: %d записей</span>", file1Data.size()));
                differencesSummary.add(String.format("    <span style='color: blue;'>Файл 2 содержит: %d записей</span>", file2Data.size()));

                Set<String> commonKeys = new HashSet<>(file1Data.keySet());
                commonKeys.retainAll(file2Data.keySet());
                differencesSummary.add(String.format("    <span style='color: orange;'>Количество совпадающих дат: %d</span>", commonKeys.size()));

                // Возвращаем результат без детальных данных
                return new ComparisonResult(differencesSummary, null);
            }

            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(file1Data.keySet());
            allKeys.addAll(file2Data.keySet());
            List<String> sortedKeys = allKeys.stream().sorted().toList();

            for (String key : sortedKeys) {
                List<List<String>> file1Rows = file1Data.getOrDefault(key, new ArrayList<>());
                List<List<String>> file2Rows = file2Data.getOrDefault(key, new ArrayList<>());

                Map<String, Integer> file1Counts = buildValueCounts(file1Rows);
                Map<String, Integer> file2Counts = buildValueCounts(file2Rows);

                if (!compareMultisets(file1Counts, file2Counts)) {
                    differencesSummary.add("Различия найдены для даты: " + key);

                    // Получаем и сводку, и детали
                    // НОВЫЙ ВЫЗОВ - передаем оригинальные строки file1Rows и file2Rows
                    DifferenceDetailsResult diffResult = generateDifferenceDetails(file1Counts, file2Counts, file1Rows, file2Rows, compareByAbsoluteValue);

                    differencesSummary.addAll(diffResult.summaryStrings());
                    mismatches.put(key, diffResult.mismatchInfo());
                }
            }

            // Создаем объект с детальными данными
            DetailedComparisonData detailedData = new DetailedComparisonData(file1Data, file2Data, mismatches);

            // Возвращаем полный результат
            return new ComparisonResult(differencesSummary, detailedData);
        }
    }

    // Создает мультимножество из списка строк
    private static Map<String, Integer> buildValueCounts(List<List<String>> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> row : rows) {
            for (String value : row) {
                // ВАЖНО: Добавляем в мапу для сравнения только НЕНУЛЕВЫЕ числовые значения
                if (isNonZeroNumeric(value)) { // <-- ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД
                    counts.put(value, counts.getOrDefault(value, 0) + 1);
                }
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

    private static DifferenceDetailsResult generateDifferenceDetails(
            Map<String, Integer> file1ValueCounts, Map<String, Integer> file2ValueCounts,
            List<List<String>> originalFile1Rows, List<List<String>> originalFile2Rows,
            boolean compareByAbsoluteValue) {

        List<String> detailsSummary = new ArrayList<>();
        List<List<String>> uniqueRowsFile1 = new ArrayList<>();
        List<List<String>> uniqueRowsFile2 = new ArrayList<>();

        // --- Логика поиска уникальных СТРОК (остается без изменений, она корректна) ---
        // Копии мап для отслеживания обработанных значений
        Map<String, Integer> tempFile1Counts = new HashMap<>(file1ValueCounts);
        Map<String, Integer> tempFile2Counts = new HashMap<>(file2ValueCounts);

        for (List<String> row : originalFile1Rows) {
            for (String value : row) {
                if (isNumeric(value) && tempFile1Counts.getOrDefault(value, 0) > tempFile2Counts.getOrDefault(value, 0)) {
                    uniqueRowsFile1.add(row);
                    tempFile1Counts.put(value, tempFile1Counts.get(value) - 1);
                    tempFile2Counts.computeIfPresent(value, (_, v) -> Math.max(0, v - 1));
                    break;
                }
            }
        }

        tempFile1Counts = new HashMap<>(file1ValueCounts);
        tempFile2Counts = new HashMap<>(file2ValueCounts);

        for (List<String> row : originalFile2Rows) {
            for (String value : row) {
                if (isNumeric(value) && tempFile2Counts.getOrDefault(value, 0) > tempFile1Counts.getOrDefault(value, 0)) {
                    uniqueRowsFile2.add(row);
                    tempFile2Counts.put(value, tempFile2Counts.get(value) - 1);
                    tempFile1Counts.computeIfPresent(value, (_, v) -> Math.max(0, v - 1));
                    break;
                }
            }
        }

        // --- ИСПРАВЛЕННАЯ ЛОГИКА ФОРМИРОВАНИЯ СВОДКИ ---
        Locale.setDefault(Locale.US);
        AtomicReference<Double> file1Sum = new AtomicReference<>(0.0);
        AtomicReference<Double> file2Sum = new AtomicReference<>(0.0);

        // Функция parseValue нам все еще нужна, но теперь она будет вызываться БЕЗОПАСНО
        Function<String, Double> parseValue = value -> {
            try {
                if (value == null || value.trim().isEmpty()) return 0.0;
                return Double.parseDouble(value.trim().replace(" ", "").replace("\u00A0", "").replace(",", "."));
            } catch (NumberFormatException e) {
                // Эта ошибка теперь практически невозможна, но оставим для безопасности
                System.err.println("Не удалось преобразовать значение: '" + value + "'");
                return 0.0;
            }
        };

        // Проходим по КЛЮЧАМ (которые гарантированно являются числами) из file1ValueCounts
        file1ValueCounts.forEach((value, count1) -> {
            int count2 = file2ValueCounts.getOrDefault(value, 0);
            if (count1 > count2) {
                double numericValue = parseValue.apply(value); // Безопасный вызов
                if (compareByAbsoluteValue) numericValue = Math.abs(numericValue);
                detailsSummary.add(String.format("    <span style='color: blue;'>В файле 1: %.2f (количество: %d)</span>", numericValue, (count1 - count2)));
                file1Sum.set(file1Sum.get() + (numericValue * (count1 - count2)));
            }
        });

        // Проходим по КЛЮЧАМ из file2ValueCounts
        file2ValueCounts.forEach((value, count2) -> {
            int count1 = file1ValueCounts.getOrDefault(value, 0);
            if (count2 > count1) {
                double numericValue = parseValue.apply(value); // Безопасный вызов
                if (compareByAbsoluteValue) numericValue = Math.abs(numericValue);
                detailsSummary.add(String.format("    <span style='color: green;'>В файле 2: %.2f (количество: %d)</span>", numericValue, (count2 - count1)));
                file2Sum.set(file2Sum.get() + (numericValue * (count2 - count1)));
            }
        });

        // --- Конец формирования сводки (добавление итогов) ---
        detailsSummary.add("<span style='color: grey;'>  Итоговая сумма не совпавших значений:</span>");
        detailsSummary.add("    В файле 1: " + String.format("<span style='color: blue;'>%.2f</span>", file1Sum.get()));
        detailsSummary.add("    В файле 2: " + String.format("<span style='color: green;'>%.2f</span>", file2Sum.get()));
        double difference = Math.abs(file1Sum.get() - file2Sum.get());
        if (difference < 0.000001) {
            detailsSummary.add(String.format("    <span style='color: black;'>Разница: %.2f</span>", 0.00));
        } else {
            detailsSummary.add(String.format("    <span style='color: red;'>Разница: %.2f</span>", difference));
        }

        MismatchInfo mismatchInfo = new MismatchInfo(uniqueRowsFile1, uniqueRowsFile2);
        return new DifferenceDetailsResult(detailsSummary, mismatchInfo);
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

    // Метод для обработки файлов Приход/Расход БЕЗ остатков (структура как Дебет/Кредит)
    private static Map<String, List<List<String>>> parsePrihodRashodAsDebitCredit(Workbook workbook) {
        Map<String, List<List<String>>> fileData = new LinkedHashMap<>();
        Sheet sheet = workbook.getSheetAt(0);
        int startRowIndex = -1;

        // Ищем строку с заголовками "Приход" и "Расход"
        for (Row row : sheet) {
            if (row == null) continue;
            boolean containsPrihod = false;
            boolean containsRashod = false;
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim().toLowerCase();
                if (value.equals("приход")) containsPrihod = true;
                if (value.equals("расход")) containsRashod = true;
            }
            if (containsPrihod && containsRashod) {
                startRowIndex = row.getRowNum() + 1;
                break;
            }
        }

        if (startRowIndex == -1) {
            return fileData;
        }

        Pattern datePattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2,4}");

        for (int i = startRowIndex; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row) || containsSummary(row)) {
                break;
            }

            List<String> rowData = new ArrayList<>();
            String dateKey = null;

            // Собираем все непустые ячейки
            for (Cell cell : row) {
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    rowData.add(value);
                }
            }

            if (rowData.isEmpty()) continue;

            // Если первый элемент - это порядковый номер, удаляем его
            if (rowData.size() > 1 && isSequentialNumber(rowData.getFirst())) {
                rowData.removeFirst();
            }

            // Ищем дату в оставшихся данных
            for (String cellValue : rowData) {
                Matcher m = datePattern.matcher(cellValue);
                if (m.find()) {
                    dateKey = convertToFullYear(m.group());
                    break;
                }
            }

            // Добавляем строку, только если в ней была найдена дата
            if (dateKey != null) {
                fileData.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(rowData);
            }
        }
        return fileData;
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

    /**
     * Проверяет, является ли строка числом, и не является ли это число нулем.
     *
     * @param str Строка для проверки.
     * @return true, если строка представляет ненулевое число, иначе false.
     */
    private static boolean isNonZeroNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            // Заменяем запятую на точку для правильного парсинга
            double value = Double.parseDouble(str.replace(",", "."));
            // Сравниваем с небольшим допуском (эпсилон) на случай ошибок с плавающей точкой
            return Math.abs(value) > 0.000001;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Проверяет, является ли строка простым целым числом (как порядковый номер),
     * корректно обрабатывая строки типа "1.0".
     */
    private static boolean isSequentialNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            // Заменяем запятую на точку для универсальности
            double d = Double.parseDouble(str.replace(",", "."));
            // Проверяем, является ли число целым (например, 1.0, 2.0, но не 1.5)
            // и оно больше нуля (порядковые номера не бывают отрицательными или нулевыми).
            if (d > 0 && d == Math.floor(d)) {
                return true;
            }
        } catch (NumberFormatException e) {
            // Если это не число (например, "текст"), то это не порядковый номер.
            return false;
        }
        return false;
    }

    /**
     * Финальная обработка найденного имени: удаление мусора и извлечение из кавычек.
     * @param rawName Исходное имя из ячейки.
     * @return Чистое, короткое имя.
     */
    private static String finalizeCounterpartyName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return null;
        }

        // Шаг 1: Удаляем все символы подчеркивания и лишние пробелы.
        String cleanedName = rawName.replace("_", "").trim();

        // Шаг 2: Ищем текст в кавычках (теперь в ЛЮБЫХ: "..." или «...»).
        // СТАРАЯ ВЕРСИЯ: Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        // НОВАЯ ВЕРСИЯ:
        Pattern pattern = Pattern.compile("[\"«](.*?)[\"»]");
        Matcher matcher = pattern.matcher(cleanedName);

        if (matcher.find()) {
            // Если нашли что-то в кавычках, возвращаем именно это.
            return matcher.group(1).trim();
        }

        // Шаг 3 (Резервный вариант): Если кавычек нет, возвращаем просто очищенную строку.
        return cleanedName;
    }

    /**
     * Ищет наименование контрагента в предоставленных файлах.
     * Проверяет файлы поочередно и возвращает первое найденное совпадение.
     * @param files Массив из двух файлов для проверки.
     * @return Наименование контрагента или "КОНТРАГЕНТ НЕ НАЙДЕН".
     */
    public static String findCounterpartyName(File[] files) {
        for (File file : files) {
            if (file == null) continue;
            try (InputStream is = new FileInputStream(file);
                 Workbook workbook = WorkbookFactory.create(is)) {

                Sheet sheet = workbook.getSheetAt(0);
                String foundName = findNameInSheet(sheet);
                if (foundName != null && !foundName.isEmpty()) {
                    return foundName; // Если имя найдено, сразу возвращаем его
                }

            } catch (Exception e) {
                // Игнорируем ошибки при чтении файла для поиска имени, чтобы не прерывать основной процесс
                System.err.println("Не удалось прочитать файл для поиска контрагента: " + file.getName());
            }
        }
        return "КОНТРАГЕНТ НЕ НАЙДЕН"; // Возвращаем, если ничего не нашли
    }

    /**
     * Вспомогательный метод для поиска имени на конкретном листе Excel.
     */
    private static String findNameInSheet(Sheet sheet) {
        for (int i = 0; i < 25 && i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell).trim();
                if (cellValue.contains("АКТ ЗВІРКИ")) {
                    return parseAktZvirky(sheet);
                }
                if (cellValue.contains("Прибуткова накладна")) {
                    return parseNakladna(sheet);
                }
            }
        }
        return null;
    }

    /**
     * Парсит лист Excel, который является "Актом сверки".
     */
    private static String parseAktZvirky(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue.contains("СМК ГРУП") && (cellValue.contains(" і ") || cellValue.contains(" та ") || cellValue.contains(" и "))) {
                    String[] parts = cellValue.split("\\s+(і|та|и)\\s+", 2);
                    if (parts.length == 2) {
                        String potentialName = parts[0].contains("СМК ГРУП") ? parts[1] : parts[0];
                        // ОТПРАВЛЯЕМ НА ФИНАЛЬНУЮ ОЧИСТКУ
                        return finalizeCounterpartyName(potentialName);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Парсит лист Excel, который является "Приходной накладной".
     */
    private static String parseNakladna(Sheet sheet) {
        for (Row row : sheet) {
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && getCellValueAsString(cell).contains("Постачальник:")) {
                    Cell nameCell = row.getCell(c + 1);
                    if (nameCell != null) {
                        String fullName = getCellValueAsString(nameCell);
                        // ОТПРАВЛЯЕМ НА ФИНАЛЬНУЮ ОЧИСТКУ
                        return finalizeCounterpartyName(fullName);
                    }
                }
            }
        }
        return null;
    }
}