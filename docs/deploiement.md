# Déploiement sur une machine virtuelle (exemple : 172.16.20.85)

Architecture : navigateur → **frontend** (port 3000, relaie `/api`) → **backend** (réseau Docker interne) → **MySQL 8 existant**
(par exemple `172.16.20.111`). Seul le port du frontend est ouvert aux utilisateurs ; le backend n'écoute que sur
`127.0.0.1:8080` de la VM.

## 1. Prérequis

- VM Linux (Ubuntu / Debian), 2 vCPU, 4 Go de RAM, 10 Go de disque.
- Accès réseau **VM → base MySQL** (`172.16.20.111:3306`) et **utilisateurs → VM** (port du frontend).
- Sur MySQL : le compte utilisé doit pouvoir se connecter **depuis l'IP de la VM** (`'admin'@'172.16.20.85'` ou `'admin'@'%'`)
  et disposer des droits `CREATE, ALTER, INDEX, REFERENCES, INSERT, UPDATE, DELETE, SELECT` sur la base. La base doit être
  **vide** à la première installation (Flyway crée le schéma et charge régions, provinces, communes, partenaires).

## 2. Installer Docker (une fois)

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER        # puis se reconnecter
docker compose version               # doit répondre
```

## 3. Récupérer le code

```bash
git clone https://github.com/BourasIsmail/CEF.git
cd CEF
git checkout main
```

## 4. Configurer `.env`

```bash
cp .env.example .env
chmod 600 .env                       # lisible par vous seul
nano .env
```

Valeurs à renseigner (ne jamais committer ce fichier) :

```ini
DB_URL=jdbc:mysql://172.16.20.111/entraide_cef_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=<compte MySQL>
DB_PASSWORD=<mot de passe MySQL>

SPRING_PROFILES_ACTIVE=prod          # prod = schéma + données de référence, sans données fictives ni Swagger
FRONTEND_PORT=80                     # ou 3000
# Adresse EXACTE saisie par les utilisateurs dans le navigateur (sinon les enregistrements sont refusés : erreur 403)
CORS_ALLOWED_ORIGINS=http://172.16.20.85
# Secret de signature des sessions : openssl rand -base64 48
JWT_SECRET=<valeur générée>
# Compte administrateur créé au premier démarrage (base sans utilisateur). Mot de passe : 12-64 caractères,
# une lettre et un chiffre, sans la partie de l'e-mail avant @
BOOTSTRAP_ADMIN_EMAIL=admin@entraide.ma
BOOTSTRAP_ADMIN_PASSWORD=<mot de passe robuste>
# HTTP (sans certificat) : false. Avec HTTPS devant la VM : true.
COOKIE_SECURE=false
```

Si `FRONTEND_PORT=3000`, mettre `CORS_ALLOWED_ORIGINS=http://172.16.20.85:3000`. Plusieurs adresses (par exemple un nom DNS) :
séparées par des virgules.

## 5. Démarrer

```bash
docker compose --profile frontend up -d --build
docker compose ps                                   # backend « healthy », frontend « running »
docker compose logs backend | grep -i "administrateur\|Started"
```

Ouvrir `http://172.16.20.85` (ou `:3000`) et se connecter avec `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`. Une fois
connecté, créer les comptes (page « الأدوار والمستخدمون »), changer ce mot de passe depuis « حسابي » et **retirer les deux
variables `BOOTSTRAP_ADMIN_*` de `.env`**.

Les conteneurs redémarrent seuls (`restart: unless-stopped`) après un redémarrage de la VM, tant que Docker démarre au boot
(`sudo systemctl enable docker`).

## 6. Pare-feu

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp                # ou 3000/tcp
sudo ufw enable
```

Le port 8080 (backend) reste fermé de l'extérieur.

## 7. Mise à jour

```bash
cd CEF && git pull
docker compose --profile frontend up -d --build
```

Les migrations de base de données s'appliquent automatiquement au démarrage. Faire une **sauvegarde MySQL avant** chaque mise à jour.

## 7 bis. Correctifs de sécurité Node.js

Seul le conteneur **frontend** exécute Node.js (image `node:22-alpine`) ; le backend est en Java. Une image déjà construite ne reçoit
pas les correctifs : après chaque bulletin de sécurité Node.js (CERT, DGSSI), reconstruire en retirant le cache et en retéléchargeant
l'image de base, puis vérifier la version :

```bash
cd CEF
docker compose --profile frontend build --pull --no-cache frontend
docker compose --profile frontend up -d
docker compose exec frontend node -v        # comparer avec les versions corrigées du bulletin
```

## 8. Sauvegardes

- **Base MySQL** : `mysqldump --single-transaction --routines -h 172.16.20.111 -u <compte> -p entraide_cef_db > cef-$(date +%F).sql`
  (à planifier chaque nuit côté serveur MySQL).
- **Pièces jointes des demandes de filière** (PDF) : volume Docker `cef-manager_uploads`.
  `docker run --rm -v cef-manager_uploads:/d -v $PWD:/b alpine tar czf /b/uploads-$(date +%F).tgz -C /d .`

## 9. HTTPS (recommandé)

Les mots de passe transitent en clair sur HTTP. Placer un reverse proxy (nginx, Caddy ou celui de la DSI) devant le frontend
avec un certificat, puis : `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS=https://<nom-du-site>`, et fermer le port public du frontend
au profit de 443.

## 10. Dépannage

| Symptôme | Cause / action |
|---|---|
| `backend ... unhealthy` | `docker compose logs backend \| grep -E "Caused by\|ERROR\|NON créé"` |
| `Communications link failure` / timeout | la VM n'atteint pas `172.16.20.111:3306` : `nc -vz 172.16.20.111 3306` |
| `Access denied for user` | mot de passe ou droit d'accès depuis l'IP de la VM |
| « Compte administrateur initial NON créé » | mot de passe trop faible ou contenant la partie de l'e-mail avant `@` ; ou la table des utilisateurs n'est pas vide |
| Connexion qui revient au login | `COOKIE_SECURE=true` alors que le site est en HTTP |
| Erreur 403 à l'enregistrement | `CORS_ALLOWED_ORIGINS` ne correspond pas exactement à l'adresse du navigateur |
