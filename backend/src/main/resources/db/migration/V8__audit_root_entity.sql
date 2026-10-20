-- ============================================================================
-- V8 : le journal d'audit rattache chaque événement à son entité « racine »
--      (ex. une inscription, une note ou un diplôme sont rattachés à leur
--      bénéficiaire), afin d'afficher l'historique complet d'une fiche.
-- ============================================================================

ALTER TABLE audit_log
    ADD COLUMN root_type VARCHAR(60) NULL AFTER entity_id,
    ADD COLUMN root_id   BIGINT      NULL AFTER root_type,
    ADD KEY idx_audit_root (root_type, root_id, occurred_at);
