# kiki — Conception

Date : 2026-10-22
Statut : en attente de validation

## 1. But

Envoyer un message depuis le téléphone du propriétaire, où qu'il soit (Wi-Fi ou 4G),
et l'afficher sur la télé du salon par-dessus n'importe quelle app en cours
(Netflix, HDMI 1 / PS5, Ibo, SmartTube…).

Une seule personne envoie : le propriétaire. Pas de comptes, pas de prénoms.

## 2. Contraintes

- Télé cible : TCL « BeyondTV », Android TV 11 (API 30), 2 Go de RAM, processeur
  4 cœurs à 900 MHz. L'app doit rester très légère et ne jamais ralentir la télé.
- Code dans un dépôt GitHub **public** : aucun secret dans le code ni dans l'historique.
- Pas d'outils Android installés sur le PC : la compilation se fait sur GitHub Actions.
- Installation sur la télé par ADB (192.168.1.222:5555), déjà opérationnel.
- Accueil de la télé : LTvLauncher. TCL bloque le démarrage automatique des apps
  inconnues (appop `APP_AUTO_START`) ; il faut l'autoriser pour kiki.

## 3. Architecture

```
Page web (GitHub Pages) --HTTP POST--> ntfy.sh/<sujet secret> --flux JSON--> App kiki (télé)
```

Trois morceaux indépendants :

### 3.1 Page web d'envoi (`web/`)
- HTML + CSS + JavaScript simples, sans framework ni étape de build.
- Publiée sur GitHub Pages depuis le dossier `web/`.
- Premier lancement : demande le code secret, le garde dans le `localStorage`
  du téléphone. Bouton « Changer de code ».
- Envoi : `POST https://ntfy.sh/<sujet>` avec le texte du message en corps.
- Retour visuel : « ✓ Envoyé » ou « ✗ Pas envoyé, réessaie ».
- Limite de saisie : 300 caractères.
- Installable sur l'écran d'accueil du téléphone (manifest web + icône kiki).
- Style : fond sombre, grande zone de texte, gros bouton bleu.

### 3.2 Relais : ntfy.sh
- Service public, gratuit, open source, sans compte.
- Le « code secret » EST le nom du sujet ntfy : une chaîne aléatoire d'au moins
  32 caractères, générée une fois, jamais commitée.
- ntfy conserve les messages 12 h, ce qui permet de récupérer un message envoyé
  pendant que la télé était en veille.

### 3.3 App télé (`app/`, Kotlin natif)
Package `com.kiki.tv`, minSdk 30, sans dépendance lourde (pas de Compose,
pas de bibliothèque réseau : `HttpURLConnection` et `org.json` suffisent).

- **Écouteur** (`ListenerService`, service de premier plan) :
  - Ouvre `GET https://ntfy.sh/<sujet>/json?since=<dernier id>` (flux long).
  - À chaque ligne `event == "message"` : transmet le texte et l'heure à la bulle,
    mémorise l'id du message.
  - Au démarrage, ne demande que le message le plus récent de moins de 12 h ;
    les plus anciens sont ignorés.
  - Coupure réseau : reconnexion automatique avec délai croissant
    (2 s, 4 s, 8 s… plafonné à 5 min), remis à zéro après une connexion réussie.
  - Notification de premier plan discrète (obligatoire pour un service de premier plan).
- **Démarrage** (`BootReceiver`) : lance l'écouteur à l'allumage de la télé.
- **Bulle** (`BubbleOverlay`) :
  - Fenêtre `TYPE_APPLICATION_OVERLAY` (permission « afficher par-dessus »).
  - Position : bord gauche, centrée verticalement, environ 1/3 de la largeur.
  - Apparence : fond noir semi-transparent, coins arrondis, contour bleu fin ;
    en-tête « 💬 kiki · HH:MM », message en grand texte blanc multi-ligne,
    pied « OK pour fermer ».
  - Un nouveau message remplace celui affiché.
  - Fermeture : appui OK (ou toute touche de la télécommande), ou au bout de 5 min.
    La fenêtre prend le focus pendant l'affichage, donc le premier appui ferme
    la bulle et n'est pas transmis à l'app en dessous ; la vidéo continue.
  - Apparition et disparition en fondu court.
- **Configuration** : le sujet secret est stocké dans les préférences de l'app,
  écrit une seule fois par ADB via une `Activity` de configuration
  (`am start ... --es topic <secret>`). Rien à taper à la télécommande.
- **Écran de l'app** (quand on l'ouvre depuis l'accueil) : état de la connexion
  (« Connecté » / « Reconnexion… ») et bouton « Afficher un message test ».

## 4. Construction et livraison

- Dépôt GitHub public `kiki` contenant `app/`, `web/`, `docs/`.
- GitHub Actions :
  - à chaque push : tests unitaires + compilation de l'APK ;
  - à chaque tag `v*` : APK signé publié dans une Release GitHub ;
  - publication de `web/` sur GitHub Pages.
- Clé de signature de l'APK : stockée dans les « secrets » GitHub du dépôt, jamais dans le code.

## 5. Installation sur la télé

1. Télécharger l'APK depuis la Release, `adb install`.
2. Accorder : `appops set com.kiki.tv SYSTEM_ALERT_WINDOW allow` et
   `cmd appops set com.kiki.tv APP_AUTO_START allow`.
3. Écrire le sujet secret via l'activité de configuration.
4. Lancer le service une première fois.

## 6. Tests

Automatiques (GitHub Actions) :
- Lecture d'une ligne ntfy : message valide, événements « keepalive »/« open » ignorés,
  JSON invalide ignoré sans plantage.
- Choix du message à afficher au démarrage (le plus récent de moins de 12 h).
- Calcul du délai de reconnexion (croissant, plafonné, remis à zéro).
- Minuterie de fermeture de la bulle à 5 min, et remplacement par un nouveau message.

Réels, sur la télé :
- Message par-dessus Netflix, par-dessus HDMI 1 (PS5), par-dessus l'accueil.
- OK ferme la bulle ; fermeture seule après 5 min.
- Envoi depuis la 4G du téléphone.
- Message reçu après une nuit et après un redémarrage de la télé.
- Mémoire utilisée par kiki au repos mesurée (objectif : moins de 30 Mo).

## 7. Hors périmètre

- Plusieurs expéditeurs, prénoms, réponses depuis la télé.
- Images, sons, historique des messages.
- Chiffrement de bout en bout (le message transite en clair chez ntfy.sh,
  protégé seulement par le secret du sujet).
