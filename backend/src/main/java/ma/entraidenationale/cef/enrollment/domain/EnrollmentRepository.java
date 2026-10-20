package ma.entraidenationale.cef.enrollment.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @EntityGraph(attributePaths = {"beneficiary", "center", "center.provincialDirectorate",
            "center.provincialDirectorate.regionalDirectorate", "filiere", "season"})
    Optional<Enrollment> findWithContextById(Long id);

    @EntityGraph(attributePaths = {"center", "filiere", "season", "sessions", "diploma"})
    List<Enrollment> findByBeneficiaryIdOrderBySeasonStartDateDesc(Long beneficiaryId);

    boolean existsByBeneficiaryIdAndSeasonId(Long beneficiaryId, Long seasonId);
}
