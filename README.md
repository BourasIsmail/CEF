# التكوين التأهيلي بمراكز التربية والتكوين – منصة التتبع (CEF-Manager)

Suivi des bénéficiaires des centres d'éducation et de formation (Entraide Nationale), de l'inscription au diplôme,
gestion des centres et des ressources humaines, demandes d'ouverture de filière, statistiques officielles.
Fonctionne sous Linux, macOS et Windows (Docker Desktop).

| Dossier | Contenu |
|---|---|
| `backend/` | Java 21, Spring Boot 4.1, Flyway, MySQL 8 : organisation, filières, bénéficiaires, inscriptions, examens, bulletin PDF, statistiques (JSON, Excel, PDF), RH, demandes de filière, sécurité (JWT en cookies httpOnly, comptes) |
| `frontend/` | Next.js (App Router), arabe RTL / français : tous les écrans |
| `docs/` | maquettes PDF, modèle de données, règles statistiques, API, frontend |

## Démarrage rapide (Docker)

Linux / macOS :

```bash
cp .env.example .env
```

Windows (PowerShell) :

```powershell
Copy-Item .env.example .env
```

Éditer `.env` : remplacer chaque `change-me` (au minimum `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`,
`BOOTSTRAP_ADMIN_PASSWORD`). Générer un secret : `openssl rand -base64 48` (Linux / macOS / Git Bash) ou, en PowerShell,
`[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])`.

```bash
docker compose --profile frontend up -d --build   # MySQL + backend + interface
```

Ouvrir <http://localhost:3000> et se connecter avec `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD`.
Sans `--profile frontend`, seuls MySQL et le backend démarrent (`http://localhost:8080/actuator/health`).

À savoir :

- **HTTP local** : mettre `COOKIE_SECURE=false` dans `.env` ; sinon le navigateur refuse les cookies de connexion.
  En production (HTTPS), laisser `true`.
- `CORS_ALLOWED_ORIGINS` doit contenir l'origine publique du frontend (défaut `http://localhost:3000`).
- Le mot de passe de l'administrateur initial ne doit pas contenir son identifiant (12 caractères minimum, une lettre
  et un chiffre). Il n'est créé que si aucun utilisateur n'existe ; retirer ces variables ensuite.
- `SPRING_PROFILES_ACTIVE=dev` charge en plus un jeu de données fictif (20 bénéficiaires, 5 centres…) et Swagger ;
  `prod` n'applique que le schéma et les données de référence.
- Les pièces jointes des demandes de filière sont dans le volume `uploads` ; la base dans `db_data`.
- **Fins de ligne** : `.gitattributes` impose LF (sauf `*.cmd`). Si le build échoue avec `./mvnw: not found` sur
  un ancien clone Windows, refaire un `git clone` (le Dockerfile tolère aussi le CRLF).

## Développement

### Backend (sans Docker)

MySQL 8 local, base vide en `utf8mb4`. Linux / macOS :

```bash
cd backend
export DB_URL='jdbc:mysql://localhost:3306/cef_manager?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USERNAME=cef DB_PASSWORD=... JWT_SECRET=... COOKIE_SECURE=false
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Windows (PowerShell) :

```powershell
cd backend
$env:DB_URL = 'jdbc:mysql://localhost:3306/cef_manager?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME = 'cef'; $env:DB_PASSWORD = '...'; $env:JWT_SECRET = '...'; $env:COOKIE_SECURE = 'false'
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

### Tests du backend

```bash
./mvnw test        # Windows : .\mvnw.cmd test   (Testcontainers : conteneur MySQL 8.4, Docker requis)
```

Sans Docker, pointer vers une base MySQL 8 **vide** : `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`
(`JWT_SECRET` est fourni par les tests). Chaque test d'API est annulé à la fin : le jeu de données reste intact.

### Frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:3000 ; /api est relayé vers BACKEND_URL (défaut http://localhost:8080)
npm run typecheck && npm run lint && npm test && npm run build
```

Le backend doit tourner en profil `dev` avec `COOKIE_SECURE=false` et `CORS_ALLOWED_ORIGINS=http://localhost:3000`.
Arabe (RTL) par défaut, français en un clic ; la langue est mémorisée dans le cookie `cef_locale` et dans le compte.

## Sécurité

Aucun secret n'est committé : tout passe par variables d'environnement (`.env.example`).

- **Authentification** : `POST /api/auth/login`. Les jetons ne sont jamais dans le corps des réponses, uniquement dans
  des cookies **httpOnly** (`SameSite=Lax`, `Secure` hors développement) : jeton d'accès JWT de 15 min, jeton de
  rafraîchissement de 7 jours renouvelé à chaque usage et stocké sous forme de hash. Un jeton rejoué coupe toutes les
  sessions de l'utilisateur ; 5 échecs de connexion verrouillent le compte 15 min.
- **CSRF** : `GET /api/auth/csrf` fournit le cookie `XSRF-TOKEN` ; le frontend renvoie sa valeur dans `X-XSRF-TOKEN`
  sur chaque écriture.
- **Profils** : ADMIN, REGIONAL (lecture), PROVINCIAL ; chacun ne voit que les données de son périmètre.
  L'administrateur gère les comptes (`/users`) ; chaque utilisateur change son mot de passe dans « Mon compte ».

## Documentation

- [Modèle de données](docs/data-model.md)
- [Règles du rapport statistique](docs/statistics-rules.md)
- [API](docs/api.md)
- [Frontend](docs/frontend.md)
- [Guide d'utilisation en arabe (PDF)](docs/guide-utilisation-ar.pdf)
- [Maquettes (PDF)](docs/CEF-Manager-maquettes.pdf)
