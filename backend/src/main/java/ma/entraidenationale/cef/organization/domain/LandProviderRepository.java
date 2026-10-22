package ma.entraidenationale.cef.organization.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LandProviderRepository extends JpaRepository<LandProvider, Long> {

    List<LandProvider> findByActiveTrueOrderByPosition();
}
