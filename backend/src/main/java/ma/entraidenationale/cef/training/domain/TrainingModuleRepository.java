package ma.entraidenationale.cef.training.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingModuleRepository extends JpaRepository<TrainingModule, Long> {

    List<TrainingModule> findByFiliereIdOrderByPosition(Long filiereId);

    long countByFiliereId(Long filiereId);
}
