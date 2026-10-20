-- معلومات إدارية إضافية : données libres (libellé / valeur) d'un agent, ajoutées sans changer la structure de la fiche
CREATE TABLE employee_extra (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id  BIGINT       NOT NULL,
    label        VARCHAR(150) NOT NULL,
    value        VARCHAR(500) NULL,
    position     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_employee_extra_employee (employee_id),
    CONSTRAINT fk_employee_extra_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
