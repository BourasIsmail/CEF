package ma.entraidenationale.cef.training.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Optional<Program> findByCode(String code);
}
