# GhostLab
[Projet de programmation réseaux du S6 2021-2022.](https://www.irif.fr/~sangnier/enseignement/Reseaux/projet-reseau-22.pdf)
# Technicalités
Le projet a été réalisé en **C et en Java**.
Le côté serveur a été réalisé en Java et le côté client en C.
# Compilation
## Client
Un _Makefile_ est disponible dans le dossier _client_. Il suffit de lancer la commande ```make``` puis de lancer l'exécutable avec ```./ghostlab <port>``` (le port est optionnel, le port par défaut est **43244**).
## Serveur
Un fichier _build.sh_ est disponible dans le dossier _server_. Plusieurs commandes sont disponibles:
 1. ```./build.sh``` pour compiler les fichiers sources
 2. ```./build.sh run <port>``` pour compiler les fichiers sources et lancer l'exécutable généré (le port est optionnel, le port par défaut est **43244**).
 ## À FINIR

