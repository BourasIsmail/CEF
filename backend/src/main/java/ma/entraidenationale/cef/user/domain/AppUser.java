package ma.entraidenationale.cef.user.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.TimestampedEntity;
import ma.entraidenationale.cef.security.AppRole;

/** Compte utilisateur. Le mot de passe n'est conservé que sous forme de hash (bcrypt). */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private AppRole role;

    @Column(name = "regional_directorate_id")
    private Long regionId;

    @Column(name = "provincial_directorate_id")
    private Long provinceId;

    @Column(name = "center_id")
    private Long centerId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "preferred_locale", nullable = false, length = 5)
    private String preferredLocale = "ar";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
