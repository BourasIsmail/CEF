package ma.entraidenationale.cef.training.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiliereRepository extends JpaRepository<Filiere, Long> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = "program")
    @Query("""
            SELECT f FROM Filiere f
            WHERE (:q IS NULL OR f.nameAr LIKE CONCAT('%', :q, '%') OR f.nameFr LIKE CONCAT('%', :q, '%')
                   OR f.code LIKE CONCAT('%', :q, '%'))
              AND (:active IS NULL OR f.active = :active)
              AND (:programId IS NULL OR f.program.id = :programId)
            """)
    Page<Filiere> search(@Param("q") String q, @Param("active") Boolean active,
                         @Param("programId") Long programId, Pageable pageable);
}
