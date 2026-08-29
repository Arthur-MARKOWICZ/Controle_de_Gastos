package br.com.controlegastos.ledger.application;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

@NamedInterface
public class LedgerEntryNotFoundException extends RuntimeException {
    public LedgerEntryNotFoundException(UUID entryId) {
        super("Lançamento não encontrado: " + entryId);
    }
}
