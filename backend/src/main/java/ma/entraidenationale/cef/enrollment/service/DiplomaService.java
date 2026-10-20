package ma.entraidenationale.cef.enrollment.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DiplomaRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DiplomaView;
import ma.entraidenationale.cef.enrollment.domain.Diploma;
import ma.entraidenationale.cef.enrollment.domain.DiplomaRepository;
import ma.entraidenationale.cef.enrollment.domain.Enrollment;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentRepository;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;

/** Diplôme : réservé aux diplômés ; numéro de série unique. */
@Service
@RequiredArgsConstructor
public class DiplomaService {

    private static final String AUDIT_TYPE = "DIPLOMA";

    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollments;
    private final DiplomaRepository diplomas;
    private final AuditService audit;

    @Transactional
    public DiplomaView save(Long enrollmentId, DiplomaRequest req) {
        Enrollment e = enrollmentService.requireManaged(enrollmentId);
        if (e.getStatus() != EnrollmentStatus.GRADUATED) {
            throw new BusinessRuleException("diploma.not_graduated", "Seuls les diplômés peuvent recevoir un diplôme");
        }
        String serial = req.serialNumber().trim();
        Diploma d = e.getDiploma();
        boolean isNew = d == null;
        if ((isNew || !serial.equals(d.getSerialNumber())) && diplomas.existsBySerialNumber(serial)) {
            throw new ConflictException("diploma.serial_exists", "Ce numéro de série est déjà attribué");
        }
        Map<String, Object> before = isNew ? null : snapshot(d);
        if (isNew) {
            d = new Diploma();
            d.setEnrollment(e);
        }
        d.setSerialNumber(serial);
        d.setReceivedDate(req.receivedDate());
        d.setAdditionalInfo(req.additionalInfo() == null || req.additionalInfo().isBlank() ? null : req.additionalInfo().trim());
        if (isNew) {
            d = diplomas.saveAndFlush(d); // persist explicite (voir ExamService)
            e.setDiploma(d);
        } else {
            diplomas.saveAndFlush(d);
        }
        Long root = e.getBeneficiary().getId();
        if (isNew) {
            audit.created(AUDIT_TYPE, d.getId(), EnrollmentService.ROOT_TYPE, root, snapshot(d));
        } else {
            audit.updated(AUDIT_TYPE, d.getId(), EnrollmentService.ROOT_TYPE, root, before, snapshot(d));
        }
        return view(d);
    }

    @Transactional
    public void delete(Long enrollmentId) {
        Enrollment e = enrollmentService.requireManaged(enrollmentId);
        Diploma d = e.getDiploma();
        if (d == null) {
            throw new NotFoundException("Aucun diplôme pour cette inscription");
        }
        audit.deleted(AUDIT_TYPE, d.getId(), EnrollmentService.ROOT_TYPE, e.getBeneficiary().getId(), snapshot(d));
        e.setDiploma(null);
        enrollments.saveAndFlush(e);
    }

    private static DiplomaView view(Diploma d) {
        return new DiplomaView(d.getId(), d.getSerialNumber(), d.getReceivedDate(), d.getAdditionalInfo());
    }

    private static Map<String, Object> snapshot(Diploma d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serialNumber", d.getSerialNumber());
        m.put("receivedDate", d.getReceivedDate() == null ? null : d.getReceivedDate().toString());
        m.put("additionalInfo", d.getAdditionalInfo());
        return m;
    }
}
