package ma.entraidenationale.cef.organization.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceTypeRepository extends JpaRepository<SpaceType, Long> {

    List<SpaceType> findAllByOrderByPosition();
}
