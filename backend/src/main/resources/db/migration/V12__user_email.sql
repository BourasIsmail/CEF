-- Connexion par adresse e-mail : l'e-mail devient l'identifiant (unique, en minuscules).
-- La colonne username est conservée en interne (nom affiché dans l'audit et le jeton) et reçoit la même valeur.
-- Les comptes existants dont l'identifiant est déjà une adresse e-mail sont migrés ; les autres doivent recevoir
-- une adresse (PUT /api/users/{id}) avant de pouvoir se connecter.
UPDATE app_user SET email = LOWER(TRIM(username)) WHERE (email IS NULL OR email = '') AND username LIKE '%@%';
UPDATE app_user SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;
ALTER TABLE app_user ADD UNIQUE KEY uk_app_user_email (email);
