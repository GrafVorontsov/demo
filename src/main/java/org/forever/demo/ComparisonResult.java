package org.forever.demo;

import java.util.List;
import java.util.Map;

/**
 * Класс-обертка для хранения всех результатов сравнения.
 *
 * @param summaryLines Сводка для главного окна (существующий функционал)
 * @param detailedData Детальные данные для окна просмотра (новый функционал)
 */
public record ComparisonResult(List<String> summaryLines,
                               org.forever.demo.ComparisonResult.DetailedComparisonData detailedData) {

    /**
     * Вложенный класс для хранения данных, необходимых для детального просмотра.
     *
     * @param file1Data  Исходные данные из файла 1 (ключ - дата/продукт)
     * @param file2Data  Исходные данные из файла 2 (ключ - дата/продукт)
     * @param mismatches Информация о несовпадениях для подсветки
     */
        public record DetailedComparisonData(Map<String, List<List<String>>> file1Data,
                                             Map<String, List<List<String>>> file2Data,
                                             Map<String, MismatchInfo> mismatches) {
    }

    /**
     * Вложенный класс для хранения уникальных значений по ключу (дате/продукту).
     *
     * @param uniqueRowsFile1 Строки, которые есть в файле 1, но отсутствуют (или их меньше) в файле 2
     * @param uniqueRowsFile2 Строки, которые есть в файле 2, но отсутствуют (или их меньше) в файле 1
     */
        public record MismatchInfo(List<List<String>> uniqueRowsFile1, List<List<String>> uniqueRowsFile2) {
    }
}