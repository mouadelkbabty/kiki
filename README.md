# kiki

kiki affiche sur une télé Android TV les messages envoyés depuis un téléphone, où que l'on soit :
une petite bulle apparaît à gauche de l'écran, par-dessus n'importe quelle app, et se ferme avec OK
ou au bout de 5 minutes. Le message passe par [ntfy.sh](https://ntfy.sh) sur un sujet secret.

Installation : télécharger l'APK de la dernière Release, l'installer avec `adb install`, autoriser
l'affichage par-dessus les autres apps, puis donner le sujet secret avec
`adb shell am start -n com.kiki.tv/.ConfigActivity --es topic <sujet>`. La page d'envoi est publiée sur GitHub Pages.
