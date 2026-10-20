package ma.entraidenationale.cef.training.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TrainingSeasonRepository extends JpaRepository<TrainingSeason, Long> {

    List<TrainingSeason> findAllByOrderByStartDateDesc();

    Optional<TrainingSeason> findByCurrentTrue();

    boolean existsByLabel(String label);

    /** Retire le drapeau « courante » de toutes les saisons (avant d'en désigner une autre). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TrainingSeason s SET s.current = false WHERE s.current = true")
    int clearCurrent();
}
