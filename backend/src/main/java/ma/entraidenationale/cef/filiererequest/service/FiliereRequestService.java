package ma.entraidenationale.cef.filiererequest.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditEntry;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.ApproveRequest;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Counts;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Detail;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.DocumentSlot;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.DocumentType;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.DocumentView;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Filter;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.HistoryEntry;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Request;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Summary;
import ma.entraidenationale.cef.filiererequest.domain.FiliereChangeRequest;
import ma.entraidenationale.cef.filiererequest.domain.FiliereChangeRequestRepository;
import ma.entraidenationale.cef.filiererequest.domain.RequestStatus;
import ma.entraidenationale.cef.filiererequest.domain.RequestType;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.service.CenterService;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.UserScope;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereDetail;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereRequest;
import ma.entraidenationale.cef.training.service.CenterFiliereService;
import ma.entraidenationale.cef.training.service.FiliereService;

/**
 * Demandes d'ouverture / modification de filière.
 *
 * <p>Circuit : la province (ou l'administrateur) prépare le dossier — 8 pièces PDF de 20 Mo maximum — et le soumet ;
 * la direction régionale le valide (ou le renvoie / le rejette) ; l'administration nationale l'approuve (ou le renvoie /
 * le rejette). À l'approbation d'une création, la filière est créée dans le catalogue ET affectée au centre ; pour une
 * modification, la filière visée est mise à jour. L'administrateur peut aussi tenir le rôle de la région
 * (profil absent ou injoignable). Chaque étape est tracée dans l'historique du circuit et dans l'audit.
 *
 * <p>Visibilité : selon le centre (hors périmètre ⇒ 404) ; les comptes régionaux ne voient pas les brouillons.
 */
@Service
@RequiredArgsConstructor
public class FiliereRequestService {

    public static final String AUDIT_TYPE = "FILIERE_REQUEST";
    private static final String AUDIT_DOCUMENT = "FILIERE_REQUEST_DOCUMENT";
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final FiliereChangeRequestRepository requests;
    private final FiliereRequestQuery query;
    private final RequestFileStorage storage;
    private final CenterService centers;
    private final FiliereService filieres;
    private final CenterFiliereService centerFilieres;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final AuditService audit;

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(Filter filter, Pageable pageable) {
        return query.search(filter, pageable);
    }

    @Transactional(readOnly = true)
    public Counts counts() {
        return query.counts();
    }

    @Transactional(readOnly = true)
    public List<DocumentType> documentTypes() {
        return jdbc.query("SELECT code, name_ar, position, mandatory FROM request_document_type ORDER BY position",
                (rs, i) -> new DocumentType(rs.getString("code"), rs.getString("name_ar"), rs.getInt("position"), rs.getBoolean("mandatory")));
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return toDetail(requireVisible(id));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> history(Long id) {
        requireVisible(id);
        return audit.historyOfRoot(AUDIT_TYPE, id, 500);
    }

    // ------------------------------------------------------------------ brouillon

    @Transactional
    public Detail create(Request req) {
        Center center = centers.requireManaged(req.centerId());
        FiliereChangeRequest r = new FiliereChangeRequest();
        r.setCenter(center);
        r.setRequestType(req.requestType());
        r.setRequestNumber(nextNumber());
        r.setCreatedByUserId(currentUser.require().id());
        r.setTargetFiliereId(req.requestType() == RequestType.MODIFY ? req.targetFiliereId() : null);
        apply(r, req);
        r = requests.saveAndFlush(r);
        history(r.getId(), null, RequestStatus.DRAFT, null);
        audit.created(AUDIT_TYPE, r.getId(), AUDIT_TYPE, r.getId(), snapshot(r));
        return toDetail(r);
    }

    @Transactional
    public Detail update(Long id, Request req) {
        FiliereChangeRequest r = requireManaged(id);
        requireStatus(r, RequestStatus.DRAFT, RequestStatus.RETURNED);
        if (req.requestType() != r.getRequestType()) {
            throw new BusinessRuleException("request.type_immutable", "Le type de la demande ne peut pas être modifié");
        }
        if (!req.centerId().equals(r.getCenter().getId())) {
            throw new BusinessRuleException("request.center_immutable", "Le centre de la demande ne peut pas être modifié");
        }
        if (r.getRequestType() == RequestType.MODIFY && !Objects.equals(req.targetFiliereId(), r.getTargetFiliereId())) {
            throw new BusinessRuleException("request.target_immutable", "La filière visée ne peut pas être modifiée");
        }
        Map<String, Object> before = snapshot(r);
        apply(r, req);
        requests.saveAndFlush(r);
        audit.updated(AUDIT_TYPE, r.getId(), AUDIT_TYPE, r.getId(), before, snapshot(r));
        return toDetail(r);
    }

    /** Seul un brouillon se supprime ; une demande déjà déposée reste au dossier. */
    @Transactional
    public void delete(Long id) {
        FiliereChangeRequest r = requireManaged(id);
        requireStatus(r, RequestStatus.DRAFT);
        List<String> keys = jdbc.queryForList("SELECT storage_key FROM request_document WHERE request_id = :id",
                new MapSqlParameterSource("id", id), String.class);
        audit.deleted(AUDIT_TYPE, r.getId(), AUDIT_TYPE, r.getId(), snapshot(r));
        requests.delete(r);
        requests.flush();
        afterCommit(() -> keys.forEach(storage::deleteQuietly));
    }

    // ------------------------------------------------------------------ pièces

    @Transactional
    public DocumentView upload(Long id, String typeCode, MultipartFile file) {
        FiliereChangeRequest r = requireManaged(id);
        requireStatus(r, RequestStatus.DRAFT, RequestStatus.RETURNED);
        Long typeId = documentTypeId(typeCode);
        byte[] content = readPdf(file);
        String filename = cleanFilename(file.getOriginalFilename());

        List<Map<String, Object>> old = jdbc.queryForList("SELECT id, storage_key FROM request_document WHERE request_id = :r AND document_type_id = :t",
                new MapSqlParameterSource("r", id).addValue("t", typeId));
        String key = storage.store(id, content);
        try {
            String sha = sha256(content);
            for (Map<String, Object> o : old) {
                jdbc.update("DELETE FROM request_document WHERE id = :id", new MapSqlParameterSource("id", o.get("id")));
            }
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO request_document (request_id, document_type_id, original_filename, size_bytes, storage_key, sha256, uploaded_by_user_id)
                    VALUES (:r, :t, :name, :size, :key, :sha, :user)""",
                    new MapSqlParameterSource("r", id).addValue("t", typeId).addValue("name", filename)
                            .addValue("size", content.length).addValue("key", key).addValue("sha", sha)
                            .addValue("user", currentUser.require().id()), keys);
            long docId = keys.getKey().longValue();
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("type", typeCode);
            snap.put("filename", filename);
            snap.put("sizeBytes", content.length);
            snap.put("sha256", sha);
            audit.created(AUDIT_DOCUMENT, docId, AUDIT_TYPE, id, snap);
            old.forEach(o -> afterCommit(() -> storage.deleteQuietly((String) o.get("storage_key"))));
            touch(r);
            return documentView(docId);
        } catch (RuntimeException e) {
            storage.deleteQuietly(key); // la ligne n'existe pas : ne pas laisser de fichier orphelin
            throw e;
        }
    }

    @Transactional
    public void deleteDocument(Long id, Long documentId) {
        FiliereChangeRequest r = requireManaged(id);
        requireStatus(r, RequestStatus.DRAFT, RequestStatus.RETURNED);
        Map<String, Object> doc = documentRow(id, documentId);
        jdbc.update("DELETE FROM request_document WHERE id = :id", new MapSqlParameterSource("id", documentId));
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("filename", doc.get("original_filename"));
        audit.deleted(AUDIT_DOCUMENT, documentId, AUDIT_TYPE, id, snap);
        afterCommit(() -> storage.deleteQuietly((String) doc.get("storage_key")));
        touch(r);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(Long id, Long documentId) {
        requireVisible(id);
        Map<String, Object> doc = documentRow(id, documentId);
        FileSystemResource resource = new FileSystemResource(storage.path((String) doc.get("storage_key")));
        if (!resource.exists()) {
            throw new NotFoundException("Fichier introuvable sur le stockage : " + documentId);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(((Number) doc.get("size_bytes")).longValue())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename((String) doc.get("original_filename"), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    // ------------------------------------------------------------------ circuit

    @Transactional
    public Detail submit(Long id) {
        FiliereChangeRequest r = requireManaged(id);
        requireStatus(r, RequestStatus.DRAFT, RequestStatus.RETURNED);
        checkComplete(r);
        Map<String, Object> before = snapshot(r);
        r.setSubmittedAt(Instant.now());
        // un nouveau dépôt repart de zéro : les décisions précédentes restent dans l'historique du circuit
        r.setRegionalDecisionBy(null);
        r.setRegionalDecisionAt(null);
        r.setRegionalComment(null);
        r.setNationalDecisionBy(null);
        r.setNationalDecisionAt(null);
        r.setNationalComment(null);
        return transition(r, RequestStatus.SUBMITTED, null, before);
    }

    /** Retrait du dépôt tant que la région n'a pas décidé. */
    @Transactional
    public Detail withdraw(Long id) {
        FiliereChangeRequest r = requireManaged(id);
        requireStatus(r, RequestStatus.SUBMITTED);
        return transition(r, RequestStatus.DRAFT, null, snapshot(r));
    }

    @Transactional
    public Detail regionalValidate(Long id, String comment) {
        FiliereChangeRequest r = requireRegional(id);
        requireStatus(r, RequestStatus.SUBMITTED);
        Map<String, Object> before = snapshot(r);
        recordRegionalDecision(r, comment);
        return transition(r, RequestStatus.REGIONAL_VALIDATED, comment, before);
    }

    @Transactional
    public Detail approve(Long id, ApproveRequest req) {
        FiliereChangeRequest r = requireNational(id);
        requireStatus(r, RequestStatus.REGIONAL_VALIDATED);
        Map<String, Object> before = snapshot(r);
        r.setResultingFiliereId(r.getRequestType() == RequestType.CREATE ? createFiliere(r, req) : modifyFiliere(r, req));
        recordNationalDecision(r, req.comment());
        return transition(r, RequestStatus.APPROVED, req.comment(), before);
    }

    @Transactional
    public Detail returnRequest(Long id, String comment) {
        return decideNegative(id, comment, RequestStatus.RETURNED);
    }

    @Transactional
    public Detail reject(Long id, String comment) {
        return decideNegative(id, comment, RequestStatus.REJECTED);
    }

    private Detail decideNegative(Long id, String comment, RequestStatus target) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessRuleException("request.comment_required", "Un commentaire est obligatoire pour renvoyer ou rejeter une demande");
        }
        FiliereChangeRequest r = requireVisible(id);
        UserScope scope = currentUser.scope();
        Map<String, Object> before = snapshot(r);
        if (r.getStatus() == RequestStatus.SUBMITTED && canActRegionally(r, scope)) {
            recordRegionalDecision(r, comment);
        } else if ((r.getStatus() == RequestStatus.SUBMITTED || r.getStatus() == RequestStatus.REGIONAL_VALIDATED) && scope.role() == AppRole.ADMIN) {
            recordNationalDecision(r, comment);
        } else if (r.getStatus() != RequestStatus.SUBMITTED && r.getStatus() != RequestStatus.REGIONAL_VALIDATED) {
            throw invalidState(r);
        } else {
            throw new AccessDeniedException("Décision non autorisée à cette étape pour votre profil");
        }
        return transition(r, target, comment.trim(), before);
    }

    // ------------------------------------------------------------------ application de la décision

    private Long createFiliere(FiliereChangeRequest r, ApproveRequest req) {
        String code = req.code() != null && !req.code().isBlank() ? req.code().trim()
                : "F" + r.getRequestNumber().substring(3); // DF-2026-0007 -> F2026-0007
        String nameFr = req.nameFr() != null && !req.nameFr().isBlank() ? req.nameFr().trim()
                : r.getProposedNameFr() != null ? r.getProposedNameFr() : r.getProposedNameAr();
        FiliereDetail created = filieres.create(new FiliereRequest(null, code, r.getProposedNameAr(), nameFr,
                r.getDurationMonths().intValue(), true));
        centerFilieres.assign(r.getCenter().getId(), created.summary().id());
        return created.summary().id();
    }

    private Long modifyFiliere(FiliereChangeRequest r, ApproveRequest req) {
        FiliereDetail current = filieres.get(r.getTargetFiliereId());
        var s = current.summary();
        String nameFr = req.nameFr() != null && !req.nameFr().isBlank() ? req.nameFr().trim()
                : r.getProposedNameFr() != null ? r.getProposedNameFr() : s.nameFr();
        int months = r.getDurationMonths() != null ? r.getDurationMonths() : s.durationMonths();
        filieres.update(s.id(), new FiliereRequest(null, s.code(), r.getProposedNameAr(), nameFr, months, null));
        return s.id();
    }

    private void recordRegionalDecision(FiliereChangeRequest r, String comment) {
        r.setRegionalDecisionBy(currentUser.require().id());
        r.setRegionalDecisionAt(Instant.now());
        r.setRegionalComment(blankToNull(comment));
    }

    private void recordNationalDecision(FiliereChangeRequest r, String comment) {
        r.setNationalDecisionBy(currentUser.require().id());
        r.setNationalDecisionAt(Instant.now());
        r.setNationalComment(blankToNull(comment));
    }

    private Detail transition(FiliereChangeRequest r, RequestStatus to, String comment, Map<String, Object> before) {
        RequestStatus from = r.getStatus();
        r.setStatus(to);
        requests.saveAndFlush(r);
        history(r.getId(), from, to, comment);
        audit.updated(AUDIT_TYPE, r.getId(), AUDIT_TYPE, r.getId(), before, snapshot(r));
        return toDetail(r);
    }

    private void history(Long requestId, RequestStatus from, RequestStatus to, String comment) {
        jdbc.update("""
                INSERT INTO request_status_history (request_id, from_status, to_status, actor_user_id, comment)
                VALUES (:r, :from, :to, :actor, :comment)""",
                new MapSqlParameterSource("r", requestId).addValue("from", from == null ? null : from.name())
                        .addValue("to", to.name()).addValue("actor", currentUser.require().id()).addValue("comment", blankToNull(comment)));
    }

    // ------------------------------------------------------------------ règles du dossier

    private void apply(FiliereChangeRequest r, Request req) {
        Center center = r.getCenter();
        if (req.associationId() != null) {
            long linked = count("SELECT COUNT(*) FROM center_association WHERE center_id = :c AND association_id = :a",
                    new MapSqlParameterSource("c", center.getId()).addValue("a", req.associationId()));
            if (linked == 0) {
                throw new BusinessRuleException("request.association_not_linked", "Cette association n'est pas liée au centre");
            }
        }
        r.setAssociationId(req.associationId());
        String nameAr = blankToNull(req.proposedNameAr());
        if (r.getRequestType() == RequestType.MODIFY) {
            if (req.targetFiliereId() == null) {
                throw new BusinessRuleException("request.target_required", "La filière à modifier est obligatoire");
            }
            String current = jdbc.query("""
                    SELECT f.name_ar FROM filiere f JOIN center_filiere cf ON cf.filiere_id = f.id
                    WHERE f.id = :f AND cf.center_id = :c""",
                    new MapSqlParameterSource("f", req.targetFiliereId()).addValue("c", center.getId()),
                    (rs, i) -> rs.getString(1)).stream().findFirst()
                    .orElseThrow(() -> new BusinessRuleException("request.target_not_at_center", "Cette filière n'est pas proposée par le centre"));
            nameAr = nameAr != null ? nameAr : current;
        } else {
            if (nameAr == null) {
                throw new BusinessRuleException("request.name_required", "Le nom de la filière est obligatoire");
            }
            if (count("SELECT COUNT(*) FROM filiere WHERE name_ar = :n", new MapSqlParameterSource("n", nameAr)) > 0) {
                throw new BusinessRuleException("request.filiere_exists",
                        "Une filière portant ce nom existe déjà au catalogue : affectez-la au centre ou déposez une demande de modification");
            }
        }
        r.setProposedNameAr(nameAr);
        r.setProposedNameFr(blankToNull(req.proposedNameFr()));
        r.setDurationMonths(req.durationMonths() == null ? null : req.durationMonths().shortValue());
        r.setExpectedBeneficiaries(req.expectedBeneficiaries());
        r.setModificationSummary(blankToNull(req.modificationSummary()));
    }

    /** Conditions de dépôt : champs obligatoires du type de demande et les 8 pièces. */
    private void checkComplete(FiliereChangeRequest r) {
        if (r.getRequestType() == RequestType.CREATE && r.getDurationMonths() == null) {
            throw new BusinessRuleException("request.duration_required", "La durée de la formation est obligatoire pour une création");
        }
        if (r.getRequestType() == RequestType.MODIFY && r.getModificationSummary() == null) {
            throw new BusinessRuleException("request.summary_required", "Le résumé des modifications est obligatoire");
        }
        List<String> missing = jdbc.queryForList("""
                SELECT t.code FROM request_document_type t
                WHERE t.mandatory AND NOT EXISTS (SELECT 1 FROM request_document d WHERE d.request_id = :r AND d.document_type_id = t.id)
                ORDER BY t.position""", new MapSqlParameterSource("r", r.getId()), String.class);
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("request.documents_missing", "Pièces manquantes : " + String.join(", ", missing));
        }
    }

    private String nextNumber() {
        String prefix = "DF-" + Year.now().getValue() + "-";
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(request_number, 9) AS UNSIGNED)), 0) FROM filiere_request WHERE request_number LIKE :p",
                new MapSqlParameterSource("p", prefix + "%"), Long.class);
        return prefix + String.format("%04d", max + 1);
    }

    // ------------------------------------------------------------------ périmètre et droits

    private FiliereChangeRequest requireVisible(Long id) {
        FiliereChangeRequest r = requests.findWithCenterById(id).orElseThrow(() -> NotFoundException.of("Demande", id));
        centers.requireVisible(r.getCenter().getId()); // hors périmètre : 404
        if (r.getStatus() == RequestStatus.DRAFT && currentUser.scope().role() == AppRole.REGIONAL) {
            throw NotFoundException.of("Demande", id); // les brouillons restent à la province
        }
        return r;
    }

    /** Dépôt et correction du dossier : administrateur ou compte provincial de la province du centre. */
    private FiliereChangeRequest requireManaged(Long id) {
        FiliereChangeRequest r = requireVisible(id);
        if (!canManage(r, currentUser.scope())) {
            throw new AccessDeniedException("Modification non autorisée sur cette demande");
        }
        return r;
    }

    private FiliereChangeRequest requireRegional(Long id) {
        FiliereChangeRequest r = requireVisible(id);
        if (!canActRegionally(r, currentUser.scope())) {
            throw new AccessDeniedException("Validation régionale non autorisée pour votre profil");
        }
        return r;
    }

    private FiliereChangeRequest requireNational(Long id) {
        FiliereChangeRequest r = requireVisible(id);
        if (currentUser.scope().role() != AppRole.ADMIN) {
            throw new AccessDeniedException("Seule l'administration nationale approuve une demande");
        }
        return r;
    }

    private static boolean canManage(FiliereChangeRequest r, UserScope scope) {
        return scope.managesProvince(r.getCenter().getProvincialDirectorate().getId());
    }

    private static boolean canActRegionally(FiliereChangeRequest r, UserScope scope) {
        if (scope.role() == AppRole.ADMIN) {
            return true;
        }
        return scope.role() == AppRole.REGIONAL
                && scope.seesRegion(r.getCenter().getProvincialDirectorate().getRegionalDirectorate().getId());
    }

    private List<String> availableActions(FiliereChangeRequest r) {
        UserScope scope = currentUser.scope();
        List<String> actions = new ArrayList<>();
        RequestStatus s = r.getStatus();
        if (canManage(r, scope)) {
            if (s == RequestStatus.DRAFT || s == RequestStatus.RETURNED) {
                actions.add("EDIT");
                actions.add("SUBMIT");
            }
            if (s == RequestStatus.DRAFT) {
                actions.add("DELETE");
            }
            if (s == RequestStatus.SUBMITTED) {
                actions.add("WITHDRAW");
            }
        }
        if (s == RequestStatus.SUBMITTED && canActRegionally(r, scope)) {
            actions.add("VALIDATE");
            actions.add("RETURN");
            actions.add("REJECT");
        }
        if (scope.role() == AppRole.ADMIN && s == RequestStatus.REGIONAL_VALIDATED) {
            actions.add("APPROVE");
            actions.add("RETURN");
            actions.add("REJECT");
        }
        return actions;
    }

    private static void requireStatus(FiliereChangeRequest r, RequestStatus... allowed) {
        for (RequestStatus s : allowed) {
            if (r.getStatus() == s) {
                return;
            }
        }
        throw invalidState(r);
    }

    private static ConflictException invalidState(FiliereChangeRequest r) {
        return new ConflictException("request.invalid_state", "Action impossible : la demande est au statut " + r.getStatus());
    }

    // ------------------------------------------------------------------ pièces : validations

    private Long documentTypeId(String code) {
        return jdbc.query("SELECT id FROM request_document_type WHERE code = :c", new MapSqlParameterSource("c", code),
                (rs, i) -> rs.getLong(1)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Type de pièce inconnu : " + code));
    }

    private static byte[] readPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("document.empty", "Le fichier est vide");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("document.too_large", "Le fichier dépasse 20 Mo");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length < PDF_MAGIC.length || !java.util.Arrays.equals(java.util.Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
                throw new BusinessRuleException("document.not_pdf", "Seuls les fichiers PDF sont acceptés");
            }
            return content;
        } catch (IOException e) {
            throw new BusinessRuleException("document.unreadable", "Le fichier n'a pas pu être lu");
        }
    }

    /** Nom d'affichage uniquement (jamais utilisé comme chemin) : sans répertoire ni caractères de contrôle. */
    static String cleanFilename(String original) {
        String name = original == null ? "document.pdf" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            name = "document.pdf";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ projection / audit

    private Detail toDetail(FiliereChangeRequest r) {
        Center c = r.getCenter();
        var p = c.getProvincialDirectorate();
        return new Detail(r.getId(), r.getRequestNumber(), r.getRequestType(), r.getStatus(),
                c.getId(), c.getNameAr(), p.getId(), p.getNameAr(), p.getRegionalDirectorate().getId(),
                r.getAssociationId(), nameOf("association", r.getAssociationId()),
                r.getTargetFiliereId(), nameOf("filiere", r.getTargetFiliereId()),
                r.getProposedNameAr(), r.getProposedNameFr(),
                r.getDurationMonths() == null ? null : r.getDurationMonths().intValue(), r.getExpectedBeneficiaries(),
                r.getModificationSummary(), r.getSubmittedAt(),
                r.getRegionalDecisionAt(), r.getRegionalComment(), r.getNationalDecisionAt(), r.getNationalComment(),
                r.getResultingFiliereId(), slots(r.getId()), historyOf(r.getId()), availableActions(r),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private List<DocumentSlot> slots(Long requestId) {
        Map<Long, DocumentView> byType = new LinkedHashMap<>();
        jdbc.query("""
                SELECT d.document_type_id, d.id, d.original_filename, d.size_bytes, d.sha256, d.uploaded_at, u.username
                FROM request_document d JOIN app_user u ON u.id = d.uploaded_by_user_id WHERE d.request_id = :r""",
                new MapSqlParameterSource("r", requestId),
                rs -> { byType.put(rs.getLong("document_type_id"), new DocumentView(rs.getLong("id"), rs.getString("original_filename"),
                        rs.getLong("size_bytes"), rs.getString("sha256"), rs.getTimestamp("uploaded_at").toInstant(), rs.getString("username"))); });
        return jdbc.query("SELECT id, code, name_ar, position, mandatory FROM request_document_type ORDER BY position",
                (rs, i) -> new DocumentSlot(rs.getString("code"), rs.getString("name_ar"), rs.getInt("position"),
                        rs.getBoolean("mandatory"), byType.get(rs.getLong("id"))));
    }

    private List<HistoryEntry> historyOf(Long requestId) {
        return jdbc.query("""
                SELECT h.from_status, h.to_status, u.username, h.comment, h.occurred_at
                FROM request_status_history h JOIN app_user u ON u.id = h.actor_user_id
                WHERE h.request_id = :r ORDER BY h.occurred_at DESC, h.id DESC""", new MapSqlParameterSource("r", requestId),
                (rs, i) -> new HistoryEntry(rs.getString("from_status") == null ? null : RequestStatus.valueOf(rs.getString("from_status")),
                        RequestStatus.valueOf(rs.getString("to_status")), rs.getString("username"), rs.getString("comment"),
                        rs.getTimestamp("occurred_at").toInstant()));
    }

    private DocumentView documentView(Long documentId) {
        return jdbc.queryForObject("""
                SELECT d.id, d.original_filename, d.size_bytes, d.sha256, d.uploaded_at, u.username
                FROM request_document d JOIN app_user u ON u.id = d.uploaded_by_user_id WHERE d.id = :id""",
                new MapSqlParameterSource("id", documentId),
                (rs, i) -> new DocumentView(rs.getLong("id"), rs.getString("original_filename"), rs.getLong("size_bytes"),
                        rs.getString("sha256"), rs.getTimestamp("uploaded_at").toInstant(), rs.getString("username")));
    }

    private Map<String, Object> documentRow(Long requestId, Long documentId) {
        return jdbc.queryForList("SELECT original_filename, size_bytes, storage_key FROM request_document WHERE id = :d AND request_id = :r",
                new MapSqlParameterSource("d", documentId).addValue("r", requestId)).stream().findFirst()
                .orElseThrow(() -> NotFoundException.of("Pièce", documentId));
    }

    /** Une pièce ajoutée ou retirée modifie le dossier : on rafraîchit sa date de mise à jour. */
    private void touch(FiliereChangeRequest r) {
        jdbc.update("UPDATE filiere_request SET updated_at = :now WHERE id = :id",
                new MapSqlParameterSource("now", Timestamp.from(Instant.now())).addValue("id", r.getId()));
    }

    private String nameOf(String table, Long id) {
        if (id == null) {
            return null;
        }
        return jdbc.query("SELECT name_ar FROM " + table + " WHERE id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> rs.getString(1)).stream().findFirst().orElse(null);
    }

    private long count(String sql, MapSqlParameterSource params) {
        return jdbc.queryForObject(sql, params, Long.class);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static Map<String, Object> snapshot(FiliereChangeRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestNumber", r.getRequestNumber());
        m.put("requestType", r.getRequestType().name());
        m.put("status", r.getStatus().name());
        m.put("center", r.getCenter().getCode());
        m.put("associationId", r.getAssociationId());
        m.put("targetFiliereId", r.getTargetFiliereId());
        m.put("proposedNameAr", r.getProposedNameAr());
        m.put("proposedNameFr", r.getProposedNameFr());
        m.put("durationMonths", r.getDurationMonths() == null ? null : r.getDurationMonths().intValue());
        m.put("expectedBeneficiaries", r.getExpectedBeneficiaries());
        m.put("modificationSummary", r.getModificationSummary());
        m.put("regionalComment", r.getRegionalComment());
        m.put("nationalComment", r.getNationalComment());
        m.put("resultingFiliereId", r.getResultingFiliereId());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
