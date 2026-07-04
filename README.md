# Ljudspelare

En enkel Android-app som spelar upp ljudfiler ett spår i taget. Ett tryck på
headset-knappen startar nästa spår; varje spår stannar av sig själv när det är slut.
Uppspelningen fortsätter i bakgrunden (även med låst skärm) tack vare en riktig
Android-tjänst (foreground service) med MediaSession.

## Så här får du en installerbar .apk

1. Skapa ett nytt repo på GitHub (kan vara privat) och ladda upp alla dessa filer
   och mappar precis som de ligger (behåll mappstrukturen).
2. Gå till fliken **Actions** i ditt repo. Bygget startar automatiskt, eller
   tryck **"Run workflow"** om det inte gör det direkt.
3. När bygget är klart (grön bock), klicka in på körningen och scrolla ner till
   **Artifacts** → ladda ner `ljudspelare-apk`. Det är en zip som innehåller
   `app-debug.apk`.
4. Överför `app-debug.apk` till din Samsung (t.ex. via Google Drive, mejl eller USB).
5. Öppna filen på telefonen. Om det är första gången du installerar en app
   utanför Play Store ber Android dig tillåta det för den appen/filhanteraren
   du använder — godkänn det, sen installeras appen som vanligt.

## Använda appen

- Öppna appen, tryck **"Välj ljudfiler"** och markera dina ljudfiler.
- Tryck play, eller använd headset-knappen.
- När ett spår tar slut stannar appen. Nästa tryck på headset-knappen spelar nästa spår.
- Spellistan sparas automatiskt mellan gångerna du öppnar appen.
