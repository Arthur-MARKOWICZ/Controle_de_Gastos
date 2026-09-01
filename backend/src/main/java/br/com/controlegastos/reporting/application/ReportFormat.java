package br.com.controlegastos.reporting.application;

import java.util.Locale;

public enum ReportFormat {
    CSV("text/csv;charset=UTF-8", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String mediaType;
    private final String extension;

    ReportFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() { return mediaType; }
    public String extension() { return extension; }

    public static ReportFormat from(String value) {
        if (value == null) throw new IllegalArgumentException("O formato é obrigatório");
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Formato de relatório inválido");
        }
    }
}
