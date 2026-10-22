package ma.entraidenationale.cef.user.api;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.entraidenationale.cef.security.AppRole;

/** Contrats d'API des comptes utilisateurs. */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * Création d'un compte. Profils ouverts : ADMIN (aucune portée), REGIONAL (+ {@code regionId}), PROVINCIAL
     * (+ {@code provinceId} ; la région est déduite). Les autres profils sont réservés à de futures extensions.
     */
    public record CreateRequest(
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank String password,
            @NotBlank @Size(max = 200) String fullName,
            @NotNull AppRole role,
            Long regionId,
            Long provinceId,
            @Pattern(regexp = "ar|fr") String preferredLocale) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 200) String email,
            @NotNull AppRole role,
            Long regionId,
            Long provinceId,
            @Pattern(regexp = "ar|fr") String preferredLocale) {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    public record ResetPasswordRequest(@NotBlank String newPassword) {
    }

    public record LocaleRequest(@NotNull @Pattern(regexp = "ar|fr") String preferredLocale) {
    }

    public record View(
            Long id, String email, String fullName, AppRole role,
            Long regionId, String regionNameAr, Long provinceId, String provinceNameAr, Long centerId,
            boolean enabled, boolean locked, String preferredLocale, Instant lastLoginAt, Instant createdAt) {
    }
}
