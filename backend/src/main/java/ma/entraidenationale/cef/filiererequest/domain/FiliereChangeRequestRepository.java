package ma.entraidenationale.cef.filiererequest.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiliereChangeRequestRepository extends JpaRepository<FiliereChangeRequest, Long> {

    @EntityGraph(attributePaths = {"center", "center.provincialDirectorate", "center.provincialDirectorate.regionalDirectorate"})
    Optional<FiliereChangeRequest> findWithCenterById(Long id);
}
