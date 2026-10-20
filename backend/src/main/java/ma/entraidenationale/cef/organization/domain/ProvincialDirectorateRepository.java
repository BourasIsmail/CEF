package ma.entraidenationale.cef.organization.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvincialDirectorateRepository extends JpaRepository<ProvincialDirectorate, Long> {

    List<ProvincialDirectorate> findByRegionalDirectorateIdOrderByCode(Long regionalDirectorateId);

    boolean existsByCode(String code);
}
