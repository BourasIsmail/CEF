package ma.entraidenationale.cef.user.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.role = ma.entraidenationale.cef.security.AppRole.ADMIN AND u.enabled = true AND u.id <> :excludedId")
    long countOtherEnabledAdmins(@Param("excludedId") Long excludedId);
}
