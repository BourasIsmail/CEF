# التكوين التأهيلي بمراكز التربية والتكوين – منصة التتبع (CEF-Manager)

Suivi des bénéficiaires des centres d'éducation et de formation (Entraide Nationale), de l'inscription au diplôme,
gestion des centres et des ressources humaines, statistiques officielles.

| Dossier | Contenu | État |
|---|---|---|
| `backend/` | Java 21, Spring Boot 4.1, Flyway, MySQL 8 | schéma ✔ · organisation, filières, bénéficiaires, inscriptions, examens ✔ · statistiques (JSON, Excel, PDF) ✔ · ressources humaines ✔ · sécurité JWT à venir |
| `frontend/` | Next.js (App Router), arabe RTL / français | à venir (Dockerfile prêt) |
| `docs/` | maquettes PDF, modèle de données, règles statistiques | ✔ |

## Démarrage (Docker)

```bash
cp .env.example .env                 # renseigner MYSQL_PASSWORD et MYSQL_ROOT_PASSWORD
docker compose up --build            # MySQL + backend
curl localhost:8080/actuator/health  # {"status":"UP",...}
```

- `SPRING_PROFILES_ACTIVE=dev` charge en plus un jeu de données fictif (20 bénéficiaires, 5 centres…) ;
  `prod` n'applique que le schéma et les données de référence.
- Les pièces jointes (demandes de filière) sont stockées dans le volume `uploads` ; la base dans `db_data`.
- L'interface sera lancée avec `docker compose --profile frontend up` une fois le dossier `frontend/` créé.

## Développement du backend (sans Docker)

```bash
cd backend
# MySQL 8 local, base vide utf8mb4 ; puis :
export DB_URL='jdbc:mysql://localhost:3306/cef_manager?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USERNAME=cef DB_PASSWORD=...
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### Tests

```bash
./mvnw test                                   # Testcontainers : conteneur MySQL 8.4 (Docker requis)

# Sans Docker : pointer vers une base MySQL 8 vide
TEST_DB_URL='jdbc:mysql://localhost:3306/cef_it?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false' \
TEST_DB_USERNAME=cef TEST_DB_PASSWORD=... ./mvnw test
```

`SchemaAndSeedIT` vérifie les migrations, le seed et les garde-fous de la base ; `OrganizationApiIT` et
`TrainingApiIT` testent l'API (périmètre par profil, droits, règles métier, CSRF). Chaque test d'API est annulé
à la fin : le jeu de données reste intact.

## Documentation

- [Modèle de données](docs/data-model.md)
- [Règles du rapport statistique](docs/statistics-rules.md)
- [API organisation / filières / modules](docs/api.md)
- [Maquettes (PDF)](docs/CEF-Manager-maquettes.pdf)

## Sécurité

Aucun secret n'est committé : tout passe par variables d'environnement (`.env.example`). La configuration de
sécurité actuelle est provisoire (seules les sondes `/actuator/health` sont publiques). L'authentification JWT
(cookies httpOnly + rafraîchissement), les profils ADMIN / REGIONAL / PROVINCIAL et le filtrage par périmètre
arrivent à l'étape « sécurité ».
