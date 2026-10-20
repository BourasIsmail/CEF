package ma.entraidenationale.cef.organization.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerTypeRepository extends JpaRepository<PartnerType, Long> {

    List<PartnerType> findByActiveTrueOrderByPosition();
}
