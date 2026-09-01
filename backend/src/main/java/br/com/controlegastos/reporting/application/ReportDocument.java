package br.com.controlegastos.reporting.application;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

public record ReportDocument(String filename, ReportFormat format, String sheetName,
                             List<String> headers, List<List<String>> rows) {

    public void writeTo(OutputStream output) throws IOException {
        if (format == ReportFormat.CSV) {
            writeCsv(output);
        } else {
            writeXlsx(output);
        }
    }

    private void writeCsv(OutputStream output) throws IOException {
        output.write(0xEF);
        output.write(0xBB);
        output.write(0xBF);
        writeCsvLine(output, headers);
        for (List<String> row : rows) writeCsvLine(output, row);
    }

    private void writeCsvLine(OutputStream output, List<String> values) throws IOException {
        String line = values.stream().map(this::csvField).collect(java.util.stream.Collectors.joining(";")) + "\r\n";
        output.write(line.getBytes(StandardCharsets.UTF_8));
    }

    private String csvField(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private void writeXlsx(OutputStream output) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try {
            var sheet = workbook.createSheet(sheetName);
            writeSheetRow(sheet, 0, headers);
            for (int index = 0; index < rows.size(); index++) writeSheetRow(sheet, index + 1, rows.get(index));
            workbook.write(output);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void writeSheetRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, List<String> values) {
        var row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.size(); index++) row.createCell(index).setCellValue(values.get(index));
    }
}
