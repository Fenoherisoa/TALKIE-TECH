# TALKIE TECH — Android local walkie-talkie

Application Android native Kotlin, RFC OFFICE. Version initiale de développement.

## Fonctionnement prévu dans cette version

- Audio PTT PCM 16 kHz mono sur le réseau Wi-Fi local.
- Nom d'utilisateur et canal virtuel configurables.
- Mode émetteur + récepteur, émetteur uniquement ou récepteur uniquement.
- Envoi UDP broadcast sur le hotspot Wi-Fi, sans serveur Internet.
- Workflow GitHub Actions pour générer un APK debug.

## Limites importantes de cette version initiale

- Le transport audio implémenté ici est le broadcast UDP sur un même réseau Wi-Fi. Le mode Wi-Fi Direct n'est **pas encore implémenté**.
- Cette version est un prototype de base et n'a pas encore été compilée ni testée sur plusieurs téléphones physiques. La réception UDP broadcast peut être filtrée par certains hotspots/téléphones.
- Pas encore de chiffrement audio, de découverte robuste des participants, de tampon anti-gigue ni de service audio garanti écran éteint.
- La latence et la prise en charge de 5 à 20 téléphones doivent être mesurées sur le matériel réel. Aucun système audio réseau ne peut garantir une latence nulle.

## Compiler l'APK avec GitHub Actions

1. Créez un dépôt GitHub nommé `TALKIE-TECH`.
2. Décompressez et téléversez **tout le contenu** de ce dossier dans le dépôt, en conservant le dossier caché `.github/workflows`.
3. Ouvrez l'onglet **Actions** du dépôt et lancez `Build TALKIE TECH APK` via **Run workflow** (ou poussez un commit sur `main`).
4. Une fois le workflow terminé, ouvrez son exécution, puis téléchargez l'artifact `TALKIE-TECH-debug-apk`.
5. Décompressez l'artifact : `app-debug.apk` est le fichier à installer pour les essais.

## Premier essai local

1. Connectez tous les téléphones au même hotspot Wi-Fi. Le hotspot n'a pas besoin d'avoir Internet.
2. Autorisez le microphone.
3. Choisissez un nom et un canal identique sur les téléphones qui doivent se parler.
4. Appuyez sur **SE CONNECTER AU WI-FI LOCAL**.
5. Maintenez **MAINTENIR POUR PARLER** pour transmettre, puis relâchez.

Note : Android demande la permission `INTERNET` pour ouvrir des sockets réseau. Le code n'utilise aucun serveur Internet et n'a pas besoin de données mobiles ; cette permission ne signifie pas que l'application se connecte à Internet.
