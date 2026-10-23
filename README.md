# kiki

kiki affiche sur une télé Android TV les messages envoyés depuis un téléphone, où que l'on soit :
une bulle apparaît à gauche de l'écran, par-dessus n'importe quelle app (texte, sticker photo,
sondage, emoji géant). Les messages passent par [ntfy.sh](https://ntfy.sh).

**Sécurité :** tout est chiffré de bout en bout (AES-256-GCM) sur le téléphone avant l'envoi.
Le sujet ntfy et la clé sont dérivés d'un code secret de 32 caractères que seuls le téléphone et la
télé connaissent ; ntfy ne voit que des données illisibles et ne conserve pas les messages.

Installation : télécharger l'APK de la dernière Release, l'installer avec `adb install`, autoriser
l'affichage par-dessus les autres apps, puis donner le code secret avec
`adb shell am start -n com.kiki.tv/.ConfigActivity --es code <code>`. La page d'envoi est publiée sur GitHub Pages.
