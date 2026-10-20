package ma.entraidenationale.cef.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Journal d'audit (ADMIN) ; l'historique d'une fiche est sur la fiche elle-même")
class AuditController {

    private final AuditService audit;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<AuditEntry> search(@RequestParam(required = false) String entityType,
                            @RequestParam(required = false) Long entityId,
                            @RequestParam(required = false) String username,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                            @RequestParam(defaultValue = "50") int size,
                            @RequestParam(defaultValue = "0") int page) {
        int limit = Math.max(1, Math.min(size, 200));
        return audit.search(entityType, entityId, username, from, to, limit, Math.max(0, page) * limit);
    }
}
