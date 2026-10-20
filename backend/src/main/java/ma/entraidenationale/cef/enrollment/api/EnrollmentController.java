package ma.entraidenationale.cef.enrollment.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.beneficiary.domain.BeneficiaryRepository;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.Bulletin;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DepartRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DiplomaRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DiplomaView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.EnrollmentRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.EnrollmentView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.SessionRequest;
import ma.entraidenationale.cef.enrollment.domain.SessionType;
import ma.entraidenationale.cef.enrollment.service.BulletinPdfExporter;
import ma.entraidenationale.cef.enrollment.service.DiplomaService;
import ma.entraidenationale.cef.enrollment.service.EnrollmentService;
import ma.entraidenationale.cef.enrollment.service.ExamService;
import ma.entraidenationale.cef.organization.service.CenterService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Inscriptions, examens et diplômes")
class EnrollmentController {

    private final EnrollmentService enrollments;
    private final ExamService exams;
    private final DiplomaService diplomas;
    private final BeneficiaryRepository beneficiaries;
    private final CenterService centers;
    private final BulletinPdfExporter bulletinPdf;

    // ------------------------------------------------------------------ inscriptions

    @PostMapping("/beneficiaries/{beneficiaryId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    EnrollmentView enroll(@PathVariable Long beneficiaryId, @Valid @RequestBody EnrollmentRequest req) {
        var b = beneficiaries.findWithCenterById(beneficiaryId)
                .orElseThrow(() -> NotFoundException.of("Bénéficiaire", beneficiaryId));
        centers.requireManaged(b.getCenter().getId());
        return enrollments.enroll(b, req);
    }

    @GetMapping("/enrollments/{id}")
    EnrollmentView get(@PathVariable Long id) {
        return enrollments.view(id);
    }

    @PutMapping("/enrollments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    EnrollmentView update(@PathVariable Long id, @Valid @RequestBody EnrollmentRequest req) {
        return enrollments.update(id, req);
    }

    @DeleteMapping("/enrollments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    void delete(@PathVariable Long id) {
        enrollments.delete(id);
    }

    /** Parti après l'inscription et avant l'examen final (date + motif). */
    @PostMapping("/enrollments/{id}/depart")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    EnrollmentView depart(@PathVariable Long id, @Valid @RequestBody DepartRequest req) {
        return enrollments.depart(id, req);
    }

    @PostMapping("/enrollments/{id}/reinstate")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    EnrollmentView reinstate(@PathVariable Long id) {
        return enrollments.reinstate(id);
    }

    /** Clôture sans rattrapage : session normale échouée → recalé, absente → absent. */
    @PostMapping("/enrollments/{id}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    EnrollmentView finalizeWithoutMakeup(@PathVariable Long id) {
        return enrollments.finalizeWithoutMakeup(id);
    }

    // ------------------------------------------------------------------ examens

    @GetMapping("/enrollments/{id}/bulletin")
    Bulletin bulletin(@PathVariable Long id) {
        return exams.bulletin(id);
    }

    /** Bulletin en PDF généré par le serveur ; {@code lang=ar} (défaut) ou {@code fr}. */
    @GetMapping("/enrollments/{id}/bulletin.pdf")
    ResponseEntity<byte[]> bulletinPdf(@PathVariable Long id, @RequestParam(defaultValue = "ar") String lang) {
        Bulletin b = exams.bulletin(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("bulletin-" + b.registrationNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bulletinPdf.export(b, lang));
    }

    @PutMapping("/enrollments/{id}/sessions/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Bulletin saveSession(@PathVariable Long id, @PathVariable SessionType type, @Valid @RequestBody SessionRequest req) {
        return exams.saveSession(id, type, req);
    }

    @DeleteMapping("/enrollments/{id}/sessions/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Bulletin deleteSession(@PathVariable Long id, @PathVariable SessionType type) {
        return exams.deleteSession(id, type);
    }

    // ------------------------------------------------------------------ diplôme

    @PutMapping("/enrollments/{id}/diploma")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    DiplomaView saveDiploma(@PathVariable Long id, @Valid @RequestBody DiplomaRequest req) {
        return diplomas.save(id, req);
    }

    @DeleteMapping("/enrollments/{id}/diploma")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    void deleteDiploma(@PathVariable Long id) {
        diplomas.delete(id);
    }
}
