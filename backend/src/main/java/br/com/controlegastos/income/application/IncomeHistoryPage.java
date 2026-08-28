package br.com.controlegastos.income.application;

import java.util.List;

public record IncomeHistoryPage(List<IncomeHistoryEntry> items, int page, int size, boolean hasNext) {
    public IncomeHistoryPage {
        items = List.copyOf(items);
    }
}
