package ma.entraidenationale.cef.beneficiary.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditEntry;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Detail;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Filter;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Request;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Summary;
import ma.entraidenationale.cef.beneficiary.domain.Beneficiary;
import ma.entraidenationale.cef.beneficiary.domain.BeneficiaryRepository;
import ma.entraidenationale.cef.beneficiary.domain.DisabilityType;
import ma.entraidenationale.cef.beneficiary.domain.FamilyStatus;
import ma.entraidenationale.cef.beneficiary.domain.MigrationStatus;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.enrollment.service.EnrollmentService;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.service.CenterService;

/**
 * Fiches des bénéficiaires. Périmètre : la fiche est visible si son centre l'est (sinon 404) ; création et
 * modification réservées à l'ADMIN et au compte provincial de la province du centre. Toute création,
 * modification ou suppression est tracée dans le journal d'audit.
 */
@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    public static final String AUDIT_TYPE = "BENEFICIARY";

    private final BeneficiaryRepository beneficiaries;
    private final BeneficiaryQuery query;
    private final CenterService centers;
    private final EnrollmentService enrollments;
    private final AuditService audit;

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(Filter filter, Pageable pageable) {
        return query.search(filter, pageable);
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return toDetail(requireVisible(id));
    }

    /** Historique complet de la fiche (fiche, inscriptions, notes, diplôme), du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<AuditEntry> history(Long id) {
        requireVisible(id);
        return audit.historyOfRoot(AUDIT_TYPE, id, 500);
    }

    // ------------------------------------------------------------------ écriture

    @Transactional
    public Detail create(Request req) {
        Center center = centers.requireManaged(req.centerId());
        Beneficiary b = new Beneficiary();
        b.setCenter(center);
        b.setRankNumber(resolveRank(center, req.rankNumber(), null));
        b.setRegistrationNumber(resolveRegistrationNumber(center, req.registrationNumber(), req.registrationDate(), null));
        apply(b, req);
        b = beneficiaries.saveAndFlush(b);
        audit.created(AUDIT_TYPE, b.getId(), AUDIT_TYPE, b.getId(), snapshot(b));
        if (req.enrollment() != null) {
            enrollments.enroll(b, req.enrollment());
        }
        return toDetail(b);
    }

    @Transactional
    public Detail update(Long id, Request req) {
        Beneficiary b = requireManaged(id);
        if (!req.centerId().equals(b.getCenter().getId())) {
            throw new BusinessRuleException("beneficiary.center_immutable", "Le centre d'un bénéficiaire ne peut pas être modifié");
        }
        Map<String, Object> before = snapshot(b);
        if (req.rankNumber() != null && req.rankNumber() != b.getRankNumber()) {
            b.setRankNumber(resolveRank(b.getCenter(), req.rankNumber(), b));
        }
        if (req.registrationNumber() != null && !req.registrationNumber().isBlank()
                && !req.registrationNumber().trim().equals(b.getRegistrationNumber())) {
            b.setRegistrationNumber(resolveRegistrationNumber(b.getCenter(), req.registrationNumber(), req.registrationDate(), b));
        }
        apply(b, req);
        beneficiaries.saveAndFlush(b);
        audit.updated(AUDIT_TYPE, b.getId(), AUDIT_TYPE, b.getId(), before, snapshot(b));
        return toDetail(b);
    }

    /** Suppression : refusée si des résultats d'examen ou un diplôme existent (on désactive / on garde la trace). */
    @Transactional
    public void delete(Long id) {
        Beneficiary b = requireManaged(id);
        if (enrollments.resultsCountOf(id) > 0) {
            throw new ConflictException("beneficiary.has_results",
                    "Suppression impossible : le bénéficiaire a des résultats d'examen ou un diplôme");
        }
        enrollments.deleteAllOf(b);
        audit.deleted(AUDIT_TYPE, b.getId(), AUDIT_TYPE, b.getId(), snapshot(b));
        beneficiaries.delete(b);
    }

    // ------------------------------------------------------------------ périmètre

    private Beneficiary requireVisible(Long id) {
        Beneficiary b = beneficiaries.findWithCenterById(id).orElseThrow(() -> NotFoundException.of("Bénéficiaire", id));
        centers.requireVisible(b.getCenter().getId()); // hors périmètre : 404
        return b;
    }

    private Beneficiary requireManaged(Long id) {
        Beneficiary b = requireVisible(id);
        centers.requireManaged(b.getCenter().getId()); // lecture seule pour les profils non gestionnaires : 403
        return b;
    }

    // ------------------------------------------------------------------ règles

    private int resolveRank(Center center, Integer requested, Beneficiary current) {
        if (requested == null) {
            return beneficiaries.maxRank(center.getId()) + 1;
        }
        if (beneficiaries.existsByCenterIdAndRankNumber(center.getId(), requested)) {
            throw new ConflictException("beneficiary.rank_exists", "Le numéro d'ordre " + requested + " est déjà attribué dans ce centre");
        }
        return requested;
    }

    /** Numéro fourni (unique dans le centre) ou généré : {année}-{nnnn}. */
    private String resolveRegistrationNumber(Center center, String requested, LocalDate registrationDate, Beneficiary current) {
        if (requested != null && !requested.isBlank()) {
            String number = requested.trim();
            if (beneficiaries.existsByCenterIdAndRegistrationNumber(center.getId(), number)) {
                throw new ConflictException("beneficiary.registration_number_exists", "Ce numéro d'inscription existe déjà dans le centre");
            }
            return number;
        }
        String prefix = registrationDate.getYear() + "-";
        long n = beneficiaries.countByCenterIdAndRegistrationNumberStartingWith(center.getId(), prefix) + 1;
        String number;
        do {
            number = prefix + String.format("%04d", n++);
        } while (beneficiaries.existsByCenterIdAndRegistrationNumber(center.getId(), number));
        return number;
    }

    private void apply(Beneficiary b, Request req) {
        b.setRegistrationDate(req.registrationDate());
        b.setGender(req.gender());
        b.setFullNameAr(req.fullNameAr().trim());
        b.setFullNameFr(blankToNull(req.fullNameFr()));
        b.setBirthDate(req.birthDate());
        b.setBirthPlace(blankToNull(req.birthPlace()));
        b.setIdDocumentType(req.idDocumentType());
        b.setIdDocumentNumber(blankToNull(req.idDocumentNumber()));
        b.setAddress(blankToNull(req.address()));
        b.setPhone(blankToNull(req.phone()));
        b.setEducationLevel(req.educationLevel());
        b.setAdditionalInfo(blankToNull(req.additionalInfo()));

        // situation familiale
        b.setFamilyStatus(req.familyStatus());
        b.setFamilyStatusOtherText(requireTextWhenOther(req.familyStatus() == FamilyStatus.OTHER,
                req.familyStatusOtherText(), "beneficiary.family_status_text_required", "Précisez la situation familiale"));

        // situation sociale : plusieurs catégories possibles
        b.setVulnerable(Boolean.TRUE.equals(req.vulnerable()));
        b.setDisabilityType(req.disabilityType());
        b.setDisabilityOtherText(requireTextWhenOther(req.disabilityType() == DisabilityType.OTHER,
                req.disabilityOtherText(), "beneficiary.disability_text_required", "Précisez le type de handicap"));
        b.setMigrationStatus(req.migrationStatus());
        b.setMigrationOtherText(requireTextWhenOther(req.migrationStatus() == MigrationStatus.OTHER,
                req.migrationOtherText(), "beneficiary.migration_text_required", "Précisez la situation de migration"));
        b.setOriginCountry(req.migrationStatus() == null ? null : blankToNull(req.originCountry()));
        b.setOtherSocialSituationText(blankToNull(req.otherSocialSituation()));
    }

    /** Précision obligatoire quand la valeur choisie est « autre » ; effacée sinon. */
    private static String requireTextWhenOther(boolean isOther, String text, String code, String message) {
        String value = blankToNull(text);
        if (isOther && value == null) {
            throw new BusinessRuleException(code, message);
        }
        return isOther ? value : null;
    }

    // ------------------------------------------------------------------ projection / audit

    private Detail toDetail(Beneficiary b) {
        Center c = b.getCenter();
        return new Detail(b.getId(), c.getId(), c.getNameAr(),
                c.getProvincialDirectorate().getId(), c.getProvincialDirectorate().getRegionalDirectorate().getId(),
                b.getRankNumber(), b.getRegistrationNumber(), b.getRegistrationDate(), b.getGender(),
                b.getFullNameAr(), b.getFullNameFr(), b.getBirthDate(), b.ageAt(LocalDate.now()), b.getBirthPlace(),
                b.getIdDocumentType(), b.getIdDocumentNumber(), b.getAddress(), b.getPhone(),
                b.getEducationLevel(), b.getFamilyStatus(), b.getFamilyStatusOtherText(), b.getAdditionalInfo(),
                b.isVulnerable(), b.getDisabilityType(), b.getDisabilityOtherText(),
                b.getMigrationStatus(), b.getMigrationOtherText(), b.getOriginCountry(),
                b.getOtherSocialSituationText(), b.statCategory(),
                enrollments.viewsOf(b.getId()),
                b.getCreatedAt(), b.getUpdatedAt(), b.getCreatedBy(), b.getUpdatedBy());
    }

    private static Map<String, Object> snapshot(Beneficiary b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("center", b.getCenter().getCode());
        m.put("rankNumber", b.getRankNumber());
        m.put("registrationNumber", b.getRegistrationNumber());
        m.put("registrationDate", String.valueOf(b.getRegistrationDate()));
        m.put("gender", b.getGender().name());
        m.put("fullNameAr", b.getFullNameAr());
        m.put("fullNameFr", b.getFullNameFr());
        m.put("birthDate", String.valueOf(b.getBirthDate()));
        m.put("birthPlace", b.getBirthPlace());
        m.put("idDocumentType", b.getIdDocumentType() == null ? null : b.getIdDocumentType().name());
        m.put("idDocumentNumber", b.getIdDocumentNumber());
        m.put("address", b.getAddress());
        m.put("phone", b.getPhone());
        m.put("educationLevel", b.getEducationLevel() == null ? null : b.getEducationLevel().name());
        m.put("familyStatus", b.getFamilyStatus() == null ? null : b.getFamilyStatus().name());
        m.put("familyStatusOtherText", b.getFamilyStatusOtherText());
        m.put("vulnerable", b.isVulnerable());
        m.put("disabilityType", b.getDisabilityType() == null ? null : b.getDisabilityType().name());
        m.put("disabilityOtherText", b.getDisabilityOtherText());
        m.put("migrationStatus", b.getMigrationStatus() == null ? null : b.getMigrationStatus().name());
        m.put("migrationOtherText", b.getMigrationOtherText());
        m.put("originCountry", b.getOriginCountry());
        m.put("otherSocialSituation", b.getOtherSocialSituationText());
        m.put("additionalInfo", b.getAdditionalInfo());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
