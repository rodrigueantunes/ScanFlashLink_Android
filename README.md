
# ScanFlashLink

ScanFlashLink est une application Android permettant de scanner des codes-barres en temps réel et d'envoyer les résultats à un serveur défini par l'utilisateur. Elle utilise **CameraX** pour la capture vidéo et **Google ML Kit** pour la reconnaissance des codes-barres.

---
<p align="center">
  <img src="https://github.com/user-attachments/assets/aca5a9a2-c2a8-4fd6-b606-e71808f83569" alt="Description" width="300">
</p>


## 📌 Fonctionnalités

- 📷 **Scan en temps réel** : Détection automatique des codes-barres sans capture manuelle.
- 📡 **Envoi au serveur** : Transmission instantanée des codes scannés à une adresse IP configurée.
- 🖼 **Affichage du scan** : Capture d’écran du scan avec affichage et validation par l’utilisateur.
- 🔄 **Reconnexion automatique** : Gestion des erreurs de connexion pour éviter les pertes de données.
- ⚙️ **Personnalisation** : Configuration de l’IP du serveur directement dans l’application.

---

## 📦 Installation

### 🔧 Prérequis

- Android **7.0 (API 24)** ou supérieur.
- Accès à Internet pour l’envoi des scans.
- Accès à la caméra (demande automatique à la première utilisation).

### 📲 Téléchargement et installation

1. **Télécharger l'APK signée** :
2. **Installer l'APK et laisser Google Analyser l'Application**.
3. **Lancer l'application.

---

## 🚀 Utilisation

1. **Démarrage** : Ouvrez l’application et entrez l’adresse IP du serveur cible.
2. **Scanner un code** : Dirigez la caméra vers un code-barres, la détection est automatique.
3. **Affichage du scan** : Un aperçu de la capture est affiché avec un bouton de validation.
4. **Envoi au serveur** : Une fois validé, le code est envoyé à l’IP configurée.

---

## 🛠 Technologies utilisées

- **Kotlin** - Langage principal.
- **Android Jetpack** - Gestion de la caméra et cycle de vie.
- **CameraX** - Pour l'affichage en temps réel.
- **ML Kit** - Détection des codes-barres.
- **Sockets** - Communication avec le serveur.

---

## 📜 Changelog

Voir le fichier [`CHANGELOG.md`](CHANGELOG.md) pour l'historique des versions.

---

## 📩 Support

Si vous rencontrez un problème ou souhaitez proposer une amélioration, ouvrez une **issue** sur ce dépôt.

---

## 📄 Licence

Ce projet est sous licence **MIT**. .
```
