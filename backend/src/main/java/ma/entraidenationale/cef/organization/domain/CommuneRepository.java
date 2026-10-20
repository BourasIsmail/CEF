package ma.entraidenationale.cef.organization.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommuneRepository extends JpaRepository<Commune, Long> {

    List<Commune> findByProvincialDirectorateIdOrderByNameAr(Long provincialDirectorateId);

    boolean existsByProvincialDirectorateIdAndNameAr(Long provincialDirectorateId, String nameAr);
}
