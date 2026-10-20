package ma.entraidenationale.cef.user.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.auth.AuthService;
import ma.entraidenationale.cef.auth.PasswordPolicy;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorate;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorateRepository;
import ma.entraidenationale.cef.organization.domain.RegionalDirectorateRepository;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.user.api.UserDtos.CreateRequest;
import ma.entraidenationale.cef.user.api.UserDtos.UpdateRequest;
import ma.entraidenationale.cef.user.api.UserDtos.View;
import ma.entraidenationale.cef.user.domain.AppUser;
import ma.entraidenationale.cef.user.domain.AppUserRepository;

/**
 * Gestion des comptes (ADMIN uniquement, voir le contrôleur). Pas de suppression : un compte se désactive, pour
 * que le journal d'audit garde un auteur identifiable. Créer, modifier, désactiver ou réinitialiser un mot de passe
 * est tracé ; les sessions de l'utilisateur sont alors révoquées (le jeton d'accès déjà émis vit au plus 15 min).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    public static final String AUDIT_TYPE = "APP_USER";

    /** Profils ouverts dans la première version ; les autres sont des extensions prévues. */
    private static final Set<AppRole> OPEN_ROLES = Set.of(AppRole.ADMIN, AppRole.REGIONAL, AppRole.PROVINCIAL);

    private final AppUserRepository users;
    private final RegionalDirectorateRepository regions;
    private final ProvincialDirectorateRepository provinces;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final AuditService audit;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<View> list(String search, AppRole role, Boolean enabled, Pageable pageable) {
        Specification<AppUser> spec = (r, q, cb) -> cb.conjunction();
        if (role != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("role"), role));
        }
        if (enabled != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("enabled"), enabled));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase().replace("%", "").replace("_", "") + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("username")), like), cb.like(cb.lower(r.get("fullName")), like)));
        }
        var page = users.findAll(spec, pageable);
        return PageResponse.of(page, page.getContent().stream().map(this::view).toList());
    }

    @Transactional(readOnly = true)
    public View get(Long id) {
        return view(require(id));
    }

    @Transactional
    public View create(CreateRequest req) {
        String username = req.username().trim();
        if (users.existsByUsername(username)) {
            throw new ConflictException("user.username_exists", "Cet identifiant existe déjà");
        }
        PasswordPolicy.check(req.password(), username);
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        applyProfile(u, req.fullName(), req.email(), req.role(), req.regionId(), req.provinceId(), req.preferredLocale());
        u = users.saveAndFlush(u);
        audit.created(AUDIT_TYPE, u.getId(), AUDIT_TYPE, u.getId(), snapshot(u));
        return view(u);
    }

    @Transactional
    public View update(Long id, UpdateRequest req) {
        AppUser u = require(id);
        boolean self = isSelf(u);
        if (self && req.role() != u.getRole()) {
            throw new BusinessRuleException("user.self_role", "Vous ne pouvez pas changer votre propre profil");
        }
        if (u.getRole() == AppRole.ADMIN && req.role() != AppRole.ADMIN && u.isEnabled()
                && users.countOtherEnabledAdmins(u.getId()) == 0) {
            throw new BusinessRuleException("user.last_admin", "Il doit rester au moins un administrateur actif");
        }
        Map<String, Object> before = snapshot(u);
        AppRole oldRole = u.getRole();
        Long oldRegion = u.getRegionId();
        Long oldProvince = u.getProvinceId();
        applyProfile(u, req.fullName(), req.email(), req.role(), req.regionId(), req.provinceId(), req.preferredLocale());
        users.saveAndFlush(u);
        audit.updated(AUDIT_TYPE, u.getId(), AUDIT_TYPE, u.getId(), before, snapshot(u));
        if (oldRole != u.getRole() || !Objects.equals(oldRegion, u.getRegionId()) || !Objects.equals(oldProvince, u.getProvinceId())) {
            auth.revokeSessions(u.getId()); // le périmètre change : il faudra se reconnecter
        }
        return view(u);
    }

    @Transactional
    public View setEnabled(Long id, boolean enabled) {
        AppUser u = require(id);
        if (!enabled) {
            if (isSelf(u)) {
                throw new BusinessRuleException("user.self_disable", "Vous ne pouvez pas désactiver votre propre compte");
            }
            if (u.getRole() == AppRole.ADMIN && u.isEnabled() && users.countOtherEnabledAdmins(u.getId()) == 0) {
                throw new BusinessRuleException("user.last_admin", "Il doit rester au moins un administrateur actif");
            }
        }
        Map<String, Object> before = snapshot(u);
        u.setEnabled(enabled);
        if (enabled) {
            u.setFailedLoginAttempts(0);
            u.setLockedUntil(null); // la réactivation lève aussi un éventuel verrouillage
        }
        users.saveAndFlush(u);
        audit.updated(AUDIT_TYPE, u.getId(), AUDIT_TYPE, u.getId(), before, snapshot(u));
        if (!enabled) {
            auth.revokeSessions(u.getId());
        }
        return view(u);
    }

    /** Réinitialisation par un administrateur : les sessions de l'utilisateur sont coupées et le verrouillage levé. */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser u = require(id);
        PasswordPolicy.check(newPassword, u.getUsername());
        u.setPasswordHash(encoder.encode(newPassword));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        users.saveAndFlush(u);
        audit.updated(AUDIT_TYPE, u.getId(), AUDIT_TYPE, u.getId(), Map.of(), Map.of("passwordResetAt", Instant.now().toString()));
        auth.revokeSessions(u.getId());
    }

    /** Langue préférée de l'utilisateur connecté. */
    @Transactional
    public View updateOwnLocale(Long id, String locale) {
        AppUser u = require(id);
        u.setPreferredLocale(locale);
        return view(users.saveAndFlush(u));
    }

    // ------------------------------------------------------------------ règles

    private void applyProfile(AppUser u, String fullName, String email, AppRole role, Long regionId, Long provinceId, String locale) {
        if (!OPEN_ROLES.contains(role)) {
            throw new BusinessRuleException("user.role_not_available", "Ce profil n'est pas encore disponible");
        }
        u.setRole(role);
        switch (role) {
            case ADMIN -> {
                u.setRegionId(null);
                u.setProvinceId(null);
            }
            case REGIONAL -> {
                if (regionId == null || !regions.existsById(regionId)) {
                    throw new BusinessRuleException("user.region_required", "Une direction régionale valide est obligatoire pour ce profil");
                }
                u.setRegionId(regionId);
                u.setProvinceId(null);
            }
            default -> { // PROVINCIAL : la région est déduite de la province
                ProvincialDirectorate p = provinceId == null ? null : provinces.findById(provinceId).orElse(null);
                if (p == null) {
                    throw new BusinessRuleException("user.province_required", "Une direction provinciale valide est obligatoire pour ce profil");
                }
                u.setProvinceId(p.getId());
                u.setRegionId(p.getRegionalDirectorate().getId());
            }
        }
        u.setCenterId(null);
        u.setFullName(fullName.trim());
        u.setEmail(email == null || email.isBlank() ? null : email.trim());
        if (locale != null) {
            u.setPreferredLocale(locale);
        }
    }

    private AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("Utilisateur", id));
    }

    private boolean isSelf(AppUser u) {
        return currentUser.find().map(p -> Objects.equals(p.id(), u.getId())).orElse(false);
    }

    // ------------------------------------------------------------------ projection

    public View view(AppUser u) {
        String region = u.getRegionId() == null ? null : regions.findById(u.getRegionId()).map(r -> r.getNameAr()).orElse(null);
        String province = u.getProvinceId() == null ? null : provinces.findById(u.getProvinceId()).map(p -> p.getNameAr()).orElse(null);
        return new View(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                u.getRegionId(), region, u.getProvinceId(), province, u.getCenterId(),
                u.isEnabled(), u.isLocked(Instant.now()), u.getPreferredLocale(), u.getLastLoginAt(), u.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public View viewOf(Long id) {
        return view(require(id));
    }

    private static Map<String, Object> snapshot(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole().name());
        m.put("regionId", u.getRegionId());
        m.put("provinceId", u.getProvinceId());
        m.put("enabled", u.isEnabled());
        m.put("preferredLocale", u.getPreferredLocale());
        return m;
    }
}
