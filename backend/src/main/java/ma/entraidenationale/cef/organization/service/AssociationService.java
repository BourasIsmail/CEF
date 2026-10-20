package ma.entraidenationale.cef.organization.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.AssociationRequest;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.AssociationView;
import ma.entraidenationale.cef.organization.domain.Association;
import ma.entraidenationale.cef.organization.domain.AssociationRepository;

/** Annuaire des associations (partagé entre les centres). */
@Service
@RequiredArgsConstructor
public class AssociationService {

    private final AssociationRepository associations;

    @Transactional(readOnly = true)
    public Page<AssociationView> search(String q, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return associations.search(query, pageable).map(AssociationService::toView);
    }

    @Transactional
    public AssociationView create(AssociationRequest req) {
        String name = req.nameAr().trim();
        if (associations.existsByNameAr(name)) {
            throw new ConflictException("association.exists", "Cette association existe déjà");
        }
        Association a = new Association();
        a.setNameAr(name);
        a.setNameFr(blankToNull(req.nameFr()));
        return toView(associations.saveAndFlush(a));
    }

    @Transactional
    public AssociationView update(Long id, AssociationRequest req) {
        Association a = associations.findById(id).orElseThrow(() -> NotFoundException.of("Association", id));
        String name = req.nameAr().trim();
        if (!name.equals(a.getNameAr()) && associations.existsByNameAr(name)) {
            throw new ConflictException("association.exists", "Cette association existe déjà");
        }
        a.setNameAr(name);
        a.setNameFr(blankToNull(req.nameFr()));
        return toView(associations.saveAndFlush(a));
    }

    private static AssociationView toView(Association a) {
        return new AssociationView(a.getId(), a.getNameAr(), a.getNameFr());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
