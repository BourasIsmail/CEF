package ma.entraidenationale.cef.enrollment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiplomaRepository extends JpaRepository<Diploma, Long> {

    boolean existsBySerialNumber(String serialNumber);
}
