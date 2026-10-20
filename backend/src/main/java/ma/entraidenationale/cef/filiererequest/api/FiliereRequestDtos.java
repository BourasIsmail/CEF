package ma.entraidenationale.cef.filiererequest.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ma.entraidenationale.cef.filiererequest.domain.RequestStatus;
import ma.entraidenationale.cef.filiererequest.domain.RequestType;

/** Contrats d'API des demandes d'ouverture / modification de filière. */
public final class FiliereRequestDtos {

    private FiliereRequestDtos() {
    }

    /**
     * Brouillon de demande. CREATE : nom arabe et durée obligatoires à la soumission. MODIFY : filière visée (déjà
     * proposée par le centre) et résumé des modifications obligatoires ; le nom proposé vaut alors, à défaut, le nom actuel.
     * Le type et le centre ne sont pas modifiables après la création.
     */
    public record Request(
            @NotNull RequestType requestType,
            @NotNull Long centerId,
            Long associationId,
            Long targetFiliereId,
            @Size(max = 200) String proposedNameAr,
            @Size(max = 200) String proposedNameFr,
            @Min(1) @Max(60) Integer durationMonths,
            @PositiveOrZero Integer expectedBeneficiaries,
            String modificationSummary) {
    }

    /** Commentaire de décision. Obligatoire pour un renvoi ou un rejet. */
    public record DecisionRequest(@Size(max = 4000) String comment) {
    }

    /** Approbation : le code de la nouvelle filière est généré s'il est absent ; le nom français vaut le nom arabe à défaut. */
    public record ApproveRequest(
            @Size(max = 4000) String comment,
            @Pattern(regexp = "[A-Za-z0-9_-]{2,30}", message = "2 à 30 caractères : lettres, chiffres, - et _") String code,
            @Size(max = 200) String nameFr) {
    }

    public record Filter(
            String search, RequestStatus status, RequestType type, Long regionId, Long provinceId, Long centerId,
            Boolean actionable) {
    }

    public record Summary(
            Long id, String requestNumber, RequestType requestType, RequestStatus status,
            Long centerId, String centerNameAr, Long provinceId, String provinceNameAr, Long regionId,
            String proposedNameAr, Long targetFiliereId, int documentsCount, Instant submittedAt, Instant createdAt, Instant updatedAt) {
    }

    public record DocumentView(Long id, String originalFilename, long sizeBytes, String sha256, Instant uploadedAt, String uploadedBy) {
    }

    /** Une des 8 pièces attendues, avec le fichier déposé s'il y en a un. */
    public record DocumentSlot(String typeCode, String typeNameAr, int position, boolean mandatory, DocumentView document) {
    }

    public record HistoryEntry(RequestStatus fromStatus, RequestStatus toStatus, String actor, String comment, Instant occurredAt) {
    }

    public record Detail(
            Long id, String requestNumber, RequestType requestType, RequestStatus status,
            Long centerId, String centerNameAr, Long provinceId, String provinceNameAr, Long regionId,
            Long associationId, String associationNameAr, Long targetFiliereId, String targetFiliereNameAr,
            String proposedNameAr, String proposedNameFr, Integer durationMonths, Integer expectedBeneficiaries,
            String modificationSummary, Instant submittedAt,
            Instant regionalDecisionAt, String regionalComment, Instant nationalDecisionAt, String nationalComment,
            Long resultingFiliereId,
            List<DocumentSlot> documents, List<HistoryEntry> history,
            /** Actions que l'utilisateur courant peut effectuer maintenant : EDIT, DELETE, SUBMIT, WITHDRAW, VALIDATE, APPROVE, RETURN, REJECT. */
            List<String> availableActions,
            Instant createdAt, Instant updatedAt) {
    }

    public record DocumentType(String code, String nameAr, int position, boolean mandatory) {
    }

    /** Nombre de demandes par statut, dans le périmètre de l'utilisateur. */
    public record Counts(Map<RequestStatus, Long> byStatus, long total) {
    }
}
