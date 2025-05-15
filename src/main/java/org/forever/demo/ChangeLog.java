package org.forever.demo;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import java.util.LinkedList;
import java.util.List;

public class ChangeLog {
    private static final List<ChangeEntry> changes = new LinkedList<>();

    static {
        // Пример добавления изменений
        addChange("0.8.5", List.of(
                "Бета-версия"
        ), Color.web("#C7C7C7"), null);
        addChange("0.9.9", List.of(
                "Первый дорелизный выпуск"
        ), Color.web("#C7C7C7"), null);
        addChange("1.1.0", List.of(
                "Добавлена проверка для \"Мінеральні Води України\""
        ), Color.web("#C7C7C7"), null);
        addChange("2.0.0", List.of(
                "Добавлено Сравнение по модулю",
                "Добавлена проверка для \"Аванта-трейд\"",
                "Добавлена проверка для ТОВ \"Арсенал ПК\""
        ), Color.web("#C7C7C7"), null);
        addChange("3.0.0", List.of(
                "Добавлена поддержка XLSX файлов",
                "Функция очистки поля вывода"
        ), Color.web("#C7C7C7"), null);
        addChange("3.2.0", List.of(
                "Защита от использования файлов с одинаковыми именами",
                "Защита если второй файл не загружен",
                "Добавлена проверка для \"БІСКВІТ І КО\""
        ), Color.web("#C7C7C7"), null);
        addChange("3.3.0", List.of(
                "Добавлена проверка для \"ЄВРОМІКС\""
        ), Color.web("#C7C7C7"), null);
        addChange("4.0.0", List.of(
                "Добавлен конвертер XLS 5.0 to XLSX"
        ), Color.web("#C7C7C7"), null);
        addChange("4.1.5", List.of(
                "Добавлена проверка для \"ЗЛАГОДА\"",
                "Добавлена проверка для \"СОЮЗ\""
        ), Color.web("#C7C7C7"), null);
        addChange("4.2.0", List.of(
                "Добавлена проверка для ТОРГОВИЙ ДІМ \"БОЙЧАК\""
        ), Color.web("#C7C7C7"), null);
        addChange("4.3.0", List.of(
                "Добавлена проверка для \"СТВ СХІД\""
        ), Color.web("#C7C7C7"), null);
        addChange("4.4.0", List.of(
                "Добавлена проверка для \"Паркторг\""
        ), Color.web("#C7C7C7"), null);
        addChange("4.5.0", List.of(
                "Добавлена функция сравнения приходных накладных"
        ), Color.web("#C7C7C7"), null);
        addChange("4.6.0", List.of(
                "Исправление сравнения приходных накладных"
        ), Color.web("#C7C7C7"), null);
        addChange("5.0.0", List.of(
                "Добавлена функция печати"
        ), Color.web("#C7C7C7"), null);
        addChange("5.6.1", List.of(
                "Добавление функции постраничной печати",
                "Исправлено неправильное масштабирование на печати"
        ), Color.web("#C7C7C7"), null);
        addChange("5.6.2", List.of(
                "Исправление функции постраничной печати"
        ), Color.web("#C7C7C7"), null);
        addChange("5.6.3", List.of(
                "Добавлена проверка для ТОВ  \"A&C\"",
                "Изменение цветового дизайна приложения",
                "Настройка меню \"История изменений\""
        ), Color.web("#C7C7C7"), null);
        addChange("6.0.0", List.of(
                "Добавлена функция автообновления"
        ), Color.web("#C7C7C7"), null);
        addChange("6.0.1", List.of(
                "Исправление функции определения формата файла"
        ), Color.web("#C7C7C7"), null);
        addChange("6.1.0", List.of(
                "Большой багфикс парсинга файлов с колонкой Сальдо"
        ), Color.web("#C7C7C7"), null);
    }

    public static List<ChangeEntry> getChanges() {
        return changes;
    }

    public static void addChange(String version, List<String> changes, Color color, Image image) {
        ChangeEntry entry = new ChangeEntry(version, changes, color, image);
        ChangeLog.changes.addFirst(entry);
    }
}