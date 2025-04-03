package org.forever.demo;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.FileMagic;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.*;

public class ExcelConverter {
    public record ExcelFileInfo(String version, boolean isConvertible, String details) {
    }

    private static final Logger logger = Logger.getLogger(ExcelConverter.class.getName());

    public static ExcelFileInfo checkExcelVersion(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            FileMagic fileMagic = FileMagic.valueOf(bis);

            if (fileMagic == FileMagic.OOXML) {
                return new ExcelFileInfo("Excel XLSX", false, "Современный формат XLSX");
            }

            if (fileMagic == FileMagic.OLE2) {
                try (FileInputStream testFis = new FileInputStream(file)) {
                    new HSSFWorkbook(testFis).close();
                    return new ExcelFileInfo("Excel 97-2003 (BIFF8)", false, "Формат Excel 97-2003");
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("BIFF5")) {
                        return new ExcelFileInfo("Excel 5.0/7.0 (BIFF5)", true, "Старый формат Excel, будет преобразован");
                    }
                    logger.log(Level.SEVERE, "Ошибка при обработке файла Excel: " + file.getName(), e);
                }
            }

            return new ExcelFileInfo("Неизвестный формат", false, "Формат файла не определен");
        }
    }

    // First part - checking and converting files
    public static File convertFile(File file1, File file2) throws Exception {
        ExcelConverter.ExcelFileInfo fileInfo = ExcelConverter.checkExcelVersion(file1);
        ExcelConverter.ExcelFileInfo fileInfo2 = ExcelConverter.checkExcelVersion(file2);

        if (fileInfo.isConvertible()) {
            return convertBiff5ToXlsx(file1);
        } else if (fileInfo2.isConvertible()) {
            return convertBiff5ToXlsx(file2);
        }
        return null; // или можно выбросить исключение, если ни один файл не подходит
    }

    // Метод конвертации, возвращающий сконвертированный файл
    public static File convertBiff5ToXlsx(File inputFile) throws Exception {
        File outputFile = new File(inputFile.getParent(),
                FilenameUtils.getBaseName(inputFile.getName()) + ".xlsx");

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if (isWindows) {
            ActiveXComponent excel = null;
            try {
                excel = new ActiveXComponent("Excel.Application");
                excel.setProperty("Visible", false);

                Dispatch workbooks = excel.getProperty("Workbooks").toDispatch();
                Dispatch workbook = Dispatch.call(workbooks, "Open",
                        inputFile.getAbsolutePath()).toDispatch();

                Dispatch.call(workbook, "SaveAs",
                        outputFile.getAbsolutePath(),
                        new Variant(51)); // xlOpenXMLWorkbook

                Dispatch.call(workbook, "Close", new Variant(false));

            } finally {
                if (excel != null) {
                    excel.invoke("Quit");
                }
            }
        } else {
            // Linux конвертация через LibreOffice
            ProcessBuilder pb = new ProcessBuilder(
                    "/usr/lib/libreoffice/program/soffice",
                    "--headless",
                    "--convert-to", "xlsx",
                    "--outdir", inputFile.getParent(),
                    inputFile.getAbsolutePath()
            );

            Process process = pb.start();
            if (process.waitFor() != 0) {
                throw new Exception("LibreOffice conversion failed");
            }

            File tempOutput = new File(inputFile.getParent(),
                    FilenameUtils.getBaseName(inputFile.getName()) + ".xlsx");
            if (!tempOutput.equals(outputFile)) {
                FileUtils.moveFile(tempOutput, outputFile);
            }
        }

        return outputFile;
    }
}