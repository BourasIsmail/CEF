package ma.entraidenationale.cef.organization.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterRepository extends JpaRepository<Center, Long>, JpaSpecificationExecutor<Center> {

    /** Liste filtrée : province, région et commune chargées d'emblée (pas de N+1). */
    @Override
    @EntityGraph(attributePaths = {"provincialDirectorate", "provincialDirectorate.regionalDirectorate", "commune"})
    Page<Center> findAll(Specification<Center> spec, Pageable pageable);

    boolean existsByCode(String code);

    long countByProvincialDirectorateId(Long provincialDirectorateId);

    /** Centre + sa province et sa région en une requête (contrôle de périmètre). */
    @Query("""
            SELECT c FROM Center c
            JOIN FETCH c.provincialDirectorate p
            JOIN FETCH p.regionalDirectorate
            JOIN FETCH c.commune
            WHERE c.id = :id
            """)
    Optional<Center> findWithOrganizationById(@Param("id") Long id);
}
