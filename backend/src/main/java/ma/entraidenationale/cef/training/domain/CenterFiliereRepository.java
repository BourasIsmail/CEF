package ma.entraidenationale.cef.training.domain;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterFiliereRepository extends JpaRepository<CenterFiliere, CenterFiliere.Key> {

    @EntityGraph(attributePaths = {"filiere", "filiere.program"})
    List<CenterFiliere> findByIdCenterId(Long centerId);

    @EntityGraph(attributePaths = {"center", "center.provincialDirectorate", "center.provincialDirectorate.regionalDirectorate"})
    List<CenterFiliere> findByIdFiliereId(Long filiereId);
}
