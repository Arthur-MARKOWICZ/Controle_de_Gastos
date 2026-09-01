package br.com.controlegastos.reporting.application;

public enum ReportType {
    EXPENSES_BY_PURPOSE("gastos-por-tipo", false),
    LIMIT_EXCEEDED_MONTHS("limites-extrapolados", true),
    GOALS_BELOW_TARGET("metas-abaixo", true);

    private final String filenamePrefix;
    private final boolean monthly;

    ReportType(String filenamePrefix, boolean monthly) {
        this.filenamePrefix = filenamePrefix;
        this.monthly = monthly;
    }

    public String filenamePrefix() { return filenamePrefix; }
    public boolean monthly() { return monthly; }
}
