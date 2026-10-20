package ma.entraidenationale.cef.organization.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssociationRepository extends JpaRepository<Association, Long> {

    boolean existsByNameAr(String nameAr);

    @Query("""
            SELECT a FROM Association a
            WHERE :q IS NULL OR a.nameAr LIKE CONCAT('%', :q, '%') OR a.nameFr LIKE CONCAT('%', :q, '%')
            """)
    Page<Association> search(@Param("q") String q, Pageable pageable);
}
