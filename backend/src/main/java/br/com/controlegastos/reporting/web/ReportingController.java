package br.com.controlegastos.reporting.web;

import br.com.controlegastos.reporting.application.ReportDocument;
import br.com.controlegastos.reporting.application.ReportFormat;
import br.com.controlegastos.reporting.application.ReportType;
import br.com.controlegastos.reporting.application.ReportingService;
import br.com.controlegastos.reporting.domain.ReportRange;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reports;

    ReportingController(ReportingService reports) {
        this.reports = reports;
    }

    @GetMapping("/expenses-by-purpose")
    ResponseEntity<StreamingResponseBody> expensesByPurpose(@RequestParam String from, @RequestParam String to,
                                                              @RequestParam String format) {
        return response(ReportType.EXPENSES_BY_PURPOSE, from, to, format);
    }

    @GetMapping("/limit-exceeded-months")
    ResponseEntity<StreamingResponseBody> limitExceededMonths(@RequestParam String from, @RequestParam String to,
                                                               @RequestParam String format) {
        return response(ReportType.LIMIT_EXCEEDED_MONTHS, from, to, format);
    }

    @GetMapping("/goals-below-target")
    ResponseEntity<StreamingResponseBody> goalsBelowTarget(@RequestParam String from, @RequestParam String to,
                                                            @RequestParam String format) {
        return response(ReportType.GOALS_BELOW_TARGET, from, to, format);
    }

    private ResponseEntity<StreamingResponseBody> response(ReportType type, String from, String to, String format) {
        ReportDocument document = reports.prepare(type, new ReportRange(parseDate(from), parseDate(to)), ReportFormat.from(format));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.format().mediaType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.filename(), StandardCharsets.UTF_8).build().toString())
                .body(document::writeTo);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Data inválida, use YYYY-MM-DD");
        }
    }
}
