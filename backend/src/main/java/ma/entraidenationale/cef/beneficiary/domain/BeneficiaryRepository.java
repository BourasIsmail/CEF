package ma.entraidenationale.cef.beneficiary.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    @EntityGraph(attributePaths = {"center", "center.provincialDirectorate", "center.provincialDirectorate.regionalDirectorate"})
    Optional<Beneficiary> findWithCenterById(Long id);

    boolean existsByCenterIdAndRankNumber(Long centerId, int rankNumber);

    boolean existsByCenterIdAndRegistrationNumber(Long centerId, String registrationNumber);

    @Query("SELECT COALESCE(MAX(b.rankNumber), 0) FROM Beneficiary b WHERE b.center.id = :centerId")
    int maxRank(@Param("centerId") Long centerId);

    long countByCenterIdAndRegistrationNumberStartingWith(Long centerId, String prefix);
}
