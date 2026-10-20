package ma.entraidenationale.cef.hr.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"provincialDirectorate", "provincialDirectorate.regionalDirectorate", "center"})
    Optional<Employee> findWithOrganizationById(Long id);

    boolean existsByRankNumber(String rankNumber);

    boolean existsByRankNumberAndIdNot(String rankNumber, Long id);
}
