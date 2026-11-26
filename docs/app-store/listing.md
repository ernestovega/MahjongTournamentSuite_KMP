# App Store listing: MTS

Store texts of the iOS app (`com.etologic.mahjongtournamentsuite`) that the Google Play listing does not have. The
description of each language is the full description of [the Google Play listing](../play-store/listing.md), so both
stores say the same. `scripts/app_store_metadata.py` builds the fastlane metadata from both files, and the workflow
Actions > Publish · App Store listing uploads it with the screenshots of `docs/app-store/<iphone|ipad>/<language>/`
(`StoreScreenshotsTest`, `MTS_STORE_FORMS=iphone,ipad`).

- App name (max 30): `MTS - Mahjong Tournament Suite`, the same in every language.
- Subtitle: max 30 characters. Keywords: max 100 bytes (a Cyrillic letter takes 2), separated by commas, without the words of the name.
  Promotional text: max 170 characters, it can change without a new version.
- Category: Sports (secondary: Board). Price: free. Age rating: 4+ (no objectionable content).
- Support URL `https://mts.mahjongmadrid.com/support`, marketing URL `https://mts.mahjongmadrid.com/`, privacy policy
  `https://mts.mahjongmadrid.com/privacy/`.

## en-US

- Subtitle: `Mahjong tournaments live`
- Keywords: `mcr,riichi,ema,ranking,tiles,scoring,score,table,round,league,club,game,board,chinese,official`
- Promotional text: `Tables, hands and rankings of every Mahjong tournament, live. Register, find your table with its QR and enter your hands from your phone.`

## es-ES

- Subtitle: `Torneos de Mahjong en directo`
- Keywords: `mcr,riichi,ema,clasificación,fichas,puntuación,mesa,ronda,liga,club,juego,tablero,chino,oficial`
- Promotional text: `Mesas, manos y clasificaciones de cada torneo de Mahjong, en directo. Inscríbete, encuentra tu mesa con su QR y apunta tus manos desde el móvil.`

## fr-FR

- Subtitle: `Tournois de Mahjong en direct`
- Keywords: `mcr,riichi,ema,classement,tuiles,score,points,table,manche,ligue,club,jeu,plateau,chinois,officiel`
- Promotional text: `Tables, mains et classements de chaque tournoi de Mahjong, en direct. Inscrivez-vous, trouvez votre table avec son QR et notez vos mains.`

## it

- Subtitle: `Tornei di Mahjong in diretta`
- Keywords: `mcr,riichi,ema,classifica,tessere,punteggio,punti,tavolo,turno,lega,club,gioco,cinese,ufficiale`
- Promotional text: `Tavoli, mani e classifiche di ogni torneo di Mahjong, in diretta. Iscriviti, trova il tuo tavolo con il QR e segna le tue mani dal telefono.`

## de-DE

- Subtitle: `Mahjong-Turniere live`
- Keywords: `mcr,riichi,ema,rangliste,steine,wertung,punkte,tisch,runde,liga,verein,spiel,brettspiel,chinesisch`
- Promotional text: `Tische, Hände und Ranglisten jedes Mahjong-Turniers, live. Melde dich an, finde deinen Tisch per QR-Code und trage deine Hände am Handy ein.`

## sv

- Subtitle: `Mahjongturneringar live`
- Keywords: `mcr,riichi,ema,ranking,brickor,poäng,bord,rond,liga,klubb,spel,brädspel,kinesisk,officiell`
- Promotional text: `Bord, händer och rankningar för varje mahjongturnering, live. Anmäl dig, hitta ditt bord med QR-koden och för in dina händer i mobilen.`

## nl-NL

- Subtitle: `Mahjongtoernooien live`
- Keywords: `mcr,riichi,ema,ranglijst,stenen,score,punten,tafel,ronde,competitie,club,spel,bordspel,chinees`
- Promotional text: `Tafels, handen en ranglijsten van elk mahjongtoernooi, live. Schrijf je in, vind je tafel met de QR-code en voer je handen in op je telefoon.`

## ru

- Subtitle: `Турниры по маджонгу онлайн`
- Keywords: `mcr,ema,рейтинг,очки,стол,раунд,лига,клуб,игра,китайский`
- Promotional text: `Столы, руки и рейтинги каждого турнира по маджонгу в реальном времени. Регистрируйтесь, находите свой стол по QR-коду и вносите руки с телефона.`
