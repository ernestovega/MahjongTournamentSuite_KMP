import type { Language } from "./i18n";

/**
 * The emails of the registrations, in the eight languages of the apps. The person gets them in the language of the
 * app that registered it. The organization gets them in the language of the host country of the tournament.
 */
export type BuiltRegistrationEmail = { subject: string; text: string; html: string };

type Paragraphs = { subject: string; paragraphs: string[]; action?: { label: string; link: string }; lines?: string[] };

type Texts = {
  verifySubject: (t: string) => string;
  verifyBody: (t: string, person: string) => string;
  verifyIgnore: string;
  verifyAction: string;
  confirmedSubject: (t: string) => string;
  confirmedBody: (t: string, person: string) => string;
  waitingSubject: (t: string) => string;
  waitingBody: (t: string, person: string, position: number) => string;
  manageIntro: string;
  manageAction: string;
  promotedSubject: (t: string) => string;
  promotedBody: (t: string, person: string) => string;
  removedSubject: (t: string) => string;
  removedBody: (t: string, contact: string) => string;
  leftSubject: (t: string) => string;
  leftBody: (t: string) => string;
  teamAddedSubject: (t: string, team: string) => string;
  teamAddedBody: (t: string, team: string, captain: string) => string;
  teamReminderSubject: (t: string) => string;
  teamReminderBody: (t: string, date: string) => string;
  orgLeftSubject: (t: string, person: string) => string;
  orgLeftBody: (t: string, person: string) => string;
  orgCounts: (confirmed: number, waiting: number) => string;
  reportSubject: (t: string) => string;
  reportIntro: (t: string) => string;
  reportNew: string;
  reportLeft: string;
  reportNone: string;
  reportNoTeam: (count: number) => string;
  reportOpenAdmin: string;
  noEmaSubject: (t: string, person: string) => string;
  noEmaBody: (t: string, person: string) => string;
  noEmaHint: string;
  noEmaMessage: string;
  /** Another registration of the tournament has the same email: fine, but one email has only one account. */
  sharedEmailNote: string;
};

const en: Texts = {
  verifySubject: (t) => `Confirm your registration in ${t}`,
  verifyBody: (t, p) => `Open the link below to confirm the registration of ${p} in ${t}. The link works for 48 hours.`,
  verifyIgnore: "If you did not ask for it, ignore this email: nothing changes.",
  verifyAction: "Confirm my registration",
  confirmedSubject: (t) => `You are registered in ${t}`,
  confirmedBody: (t, p) => `${p}, your registration in ${t} is confirmed. You have a place.`,
  waitingSubject: (t) => `You are on the waiting list of ${t}`,
  waitingBody: (t, p, n) => `${p}, the places of ${t} are full. You are number ${n} on the waiting list. We will write to you when a place is yours.`,
  manageIntro: "With this link you can see your registration, form or leave a team, change your food restrictions or cancel it:",
  manageAction: "Manage my registration",
  promotedSubject: (t) => `You have a place in ${t}`,
  promotedBody: (t, p) => `${p}, a place is free in ${t} and it is yours. Your registration is confirmed.`,
  removedSubject: (t) => `Your registration in ${t} was cancelled`,
  removedBody: (t, c) => `The organization cancelled your registration in ${t}. If this is a mistake, write to ${c}.`,
  leftSubject: (t) => `You left ${t}`,
  leftBody: (t) => `Your registration in ${t} is cancelled, as you asked. The organization knows.`,
  teamAddedSubject: (t, team) => `You are in the team ${team} of ${t}`,
  teamAddedBody: (t, team, c) => `${c} added you to the team ${team} in ${t}. If this is a mistake, you can leave the team with the link below.`,
  teamReminderSubject: (t) => `Your team for ${t}`,
  teamReminderBody: (t, d) => `The registrations of ${t} close on ${d}, and you have no full team yet (a team has 4 players). Form your team or join one before then. If not, the organization will choose your team.`,
  orgLeftSubject: (t, p) => `Cancellation in ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} cancelled the registration in ${t}.`,
  orgCounts: (c, w) => `Now: ${c} with a place, ${w} on the waiting list.`,
  reportSubject: (t) => `Weekly report of the registrations of ${t}`,
  reportIntro: (t) => `The registrations of ${t} this week.`,
  reportNew: "New registrations:",
  reportLeft: "Cancellations:",
  reportNone: "None.",
  reportNoTeam: (n) => `Players without a full team: ${n}.`,
  reportOpenAdmin: "Open the registrations",
  noEmaSubject: (t, p) => `Registration without EMA number in ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} wants to register in ${t} and has no EMA number.`,
  noEmaHint: "Add the person in the EMA players of the management app, and then add the registration in Registrations. Reply to this email to write to the person.",
  noEmaMessage: "Message:",
  sharedEmailNote: "Another registration of this tournament has this same email. That is fine: one email can register several people, for example a parent and a child. But one email can have only one account in the app: whoever wants their own account needs their own email. Many providers, such as Gmail, take an alias with +, for example name+child@gmail.com: it reaches the same inbox and counts as another email.",
};

const es: Texts = {
  verifySubject: (t) => `Confirma tu inscripción en ${t}`,
  verifyBody: (t, p) => `Abre el enlace de abajo para confirmar la inscripción de ${p} en ${t}. El enlace funciona durante 48 horas.`,
  verifyIgnore: "Si no lo pediste, ignora este correo: no cambia nada.",
  verifyAction: "Confirmar mi inscripción",
  confirmedSubject: (t) => `Estás inscrito en ${t}`,
  confirmedBody: (t, p) => `${p}, tu inscripción en ${t} está confirmada. Tienes plaza.`,
  waitingSubject: (t) => `Estás en la lista de espera de ${t}`,
  waitingBody: (t, p, n) => `${p}, las plazas de ${t} están completas. Eres el número ${n} de la lista de espera. Te escribiremos cuando tengas plaza.`,
  manageIntro: "Con este enlace puedes ver tu inscripción, formar un equipo o salir de él, cambiar tus restricciones alimentarias o darte de baja:",
  manageAction: "Gestionar mi inscripción",
  promotedSubject: (t) => `Tienes plaza en ${t}`,
  promotedBody: (t, p) => `${p}, ha quedado una plaza libre en ${t} y es tuya. Tu inscripción está confirmada.`,
  removedSubject: (t) => `Tu inscripción en ${t} se ha cancelado`,
  removedBody: (t, c) => `La organización ha cancelado tu inscripción en ${t}. Si es un error, escribe a ${c}.`,
  leftSubject: (t) => `Te has dado de baja de ${t}`,
  leftBody: (t) => `Tu inscripción en ${t} está cancelada, como pediste. La organización ya lo sabe.`,
  teamAddedSubject: (t, team) => `Estás en el equipo ${team} de ${t}`,
  teamAddedBody: (t, team, c) => `${c} te ha añadido al equipo ${team} en ${t}. Si es un error, puedes salir del equipo con el enlace de abajo.`,
  teamReminderSubject: (t) => `Tu equipo para ${t}`,
  teamReminderBody: (t, d) => `Las inscripciones de ${t} se cierran el ${d} y aún no tienes un equipo completo (un equipo tiene 4 jugadores). Forma tu equipo o únete a uno antes de esa fecha. Si no, la organización elegirá tu equipo.`,
  orgLeftSubject: (t, p) => `Baja en ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} se ha dado de baja de ${t}.`,
  orgCounts: (c, w) => `Ahora: ${c} con plaza y ${w} en lista de espera.`,
  reportSubject: (t) => `Informe semanal de las inscripciones de ${t}`,
  reportIntro: (t) => `Las inscripciones de ${t} de esta semana.`,
  reportNew: "Altas:",
  reportLeft: "Bajas:",
  reportNone: "Ninguna.",
  reportNoTeam: (n) => `Jugadores sin equipo completo: ${n}.`,
  reportOpenAdmin: "Abrir las inscripciones",
  noEmaSubject: (t, p) => `Inscripción sin número EMA en ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} quiere inscribirse en ${t} y no tiene número EMA.`,
  noEmaHint: "Da de alta a la persona en Jugadores EMA de la app de gestión y añade después su inscripción en Inscripciones. Responde a este correo para escribir a la persona.",
  noEmaMessage: "Mensaje:",
  sharedEmailNote: "Otra inscripción de este torneo tiene este mismo email. No pasa nada: un email puede inscribir a varias personas, por ejemplo a un padre y a su hijo. Pero un email solo puede tener una cuenta en la app: quien quiera su propia cuenta necesita su propio email. Muchos proveedores, como Gmail, aceptan un alias con +, por ejemplo nombre+hijo@gmail.com: llega al mismo buzón y cuenta como otro email.",
};

const fr: Texts = {
  verifySubject: (t) => `Confirmez votre inscription à ${t}`,
  verifyBody: (t, p) => `Ouvrez le lien ci-dessous pour confirmer l'inscription de ${p} à ${t}. Le lien fonctionne pendant 48 heures.`,
  verifyIgnore: "Si vous ne l'avez pas demandé, ignorez cet e-mail : rien ne change.",
  verifyAction: "Confirmer mon inscription",
  confirmedSubject: (t) => `Vous êtes inscrit à ${t}`,
  confirmedBody: (t, p) => `${p}, votre inscription à ${t} est confirmée. Vous avez une place.`,
  waitingSubject: (t) => `Vous êtes sur la liste d'attente de ${t}`,
  waitingBody: (t, p, n) => `${p}, les places de ${t} sont complètes. Vous êtes le numéro ${n} de la liste d'attente. Nous vous écrirons quand une place sera à vous.`,
  manageIntro: "Avec ce lien, vous pouvez voir votre inscription, former une équipe ou la quitter, changer vos restrictions alimentaires ou l'annuler :",
  manageAction: "Gérer mon inscription",
  promotedSubject: (t) => `Vous avez une place à ${t}`,
  promotedBody: (t, p) => `${p}, une place s'est libérée à ${t} et elle est à vous. Votre inscription est confirmée.`,
  removedSubject: (t) => `Votre inscription à ${t} a été annulée`,
  removedBody: (t, c) => `L'organisation a annulé votre inscription à ${t}. S'il s'agit d'une erreur, écrivez à ${c}.`,
  leftSubject: (t) => `Vous avez quitté ${t}`,
  leftBody: (t) => `Votre inscription à ${t} est annulée, comme vous l'avez demandé. L'organisation est prévenue.`,
  teamAddedSubject: (t, team) => `Vous êtes dans l'équipe ${team} de ${t}`,
  teamAddedBody: (t, team, c) => `${c} vous a ajouté à l'équipe ${team} à ${t}. S'il s'agit d'une erreur, vous pouvez quitter l'équipe avec le lien ci-dessous.`,
  teamReminderSubject: (t) => `Votre équipe pour ${t}`,
  teamReminderBody: (t, d) => `Les inscriptions de ${t} ferment le ${d} et vous n'avez pas encore d'équipe complète (une équipe compte 4 joueurs). Formez votre équipe ou rejoignez-en une avant cette date. Sinon, l'organisation choisira votre équipe.`,
  orgLeftSubject: (t, p) => `Désinscription à ${t} : ${p}`,
  orgLeftBody: (t, p) => `${p} a annulé son inscription à ${t}.`,
  orgCounts: (c, w) => `Maintenant : ${c} avec une place et ${w} sur la liste d'attente.`,
  reportSubject: (t) => `Rapport hebdomadaire des inscriptions de ${t}`,
  reportIntro: (t) => `Les inscriptions de ${t} cette semaine.`,
  reportNew: "Nouvelles inscriptions :",
  reportLeft: "Désinscriptions :",
  reportNone: "Aucune.",
  reportNoTeam: (n) => `Joueurs sans équipe complète : ${n}.`,
  reportOpenAdmin: "Ouvrir les inscriptions",
  noEmaSubject: (t, p) => `Inscription sans numéro EMA à ${t} : ${p}`,
  noEmaBody: (t, p) => `${p} veut s'inscrire à ${t} et n'a pas de numéro EMA.`,
  noEmaHint: "Ajoutez la personne dans les joueurs EMA de l'app de gestion, puis ajoutez son inscription dans Inscriptions. Répondez à cet e-mail pour écrire à la personne.",
  noEmaMessage: "Message :",
  sharedEmailNote: "Une autre inscription à ce tournoi a ce même email. Ce n'est pas un problème : un email peut inscrire plusieurs personnes, par exemple un parent et son enfant. Mais un email ne peut avoir qu'un seul compte dans l'app : qui veut son propre compte a besoin de son propre email. Beaucoup de fournisseurs, comme Gmail, acceptent un alias avec +, par exemple nom+enfant@gmail.com : il arrive dans la même boîte et compte comme un autre email.",
};

const it: Texts = {
  verifySubject: (t) => `Conferma la tua iscrizione a ${t}`,
  verifyBody: (t, p) => `Apri il link qui sotto per confermare l'iscrizione di ${p} a ${t}. Il link funziona per 48 ore.`,
  verifyIgnore: "Se non l'hai chiesto tu, ignora questa email: non cambia nulla.",
  verifyAction: "Conferma la mia iscrizione",
  confirmedSubject: (t) => `Sei iscritto a ${t}`,
  confirmedBody: (t, p) => `${p}, la tua iscrizione a ${t} è confermata. Hai un posto.`,
  waitingSubject: (t) => `Sei in lista d'attesa per ${t}`,
  waitingBody: (t, p, n) => `${p}, i posti di ${t} sono esauriti. Sei il numero ${n} della lista d'attesa. Ti scriveremo quando un posto sarà tuo.`,
  manageIntro: "Con questo link puoi vedere la tua iscrizione, formare una squadra o lasciarla, cambiare le tue restrizioni alimentari o annullarla:",
  manageAction: "Gestisci la mia iscrizione",
  promotedSubject: (t) => `Hai un posto a ${t}`,
  promotedBody: (t, p) => `${p}, si è liberato un posto a ${t} ed è tuo. La tua iscrizione è confermata.`,
  removedSubject: (t) => `La tua iscrizione a ${t} è stata annullata`,
  removedBody: (t, c) => `L'organizzazione ha annullato la tua iscrizione a ${t}. Se è un errore, scrivi a ${c}.`,
  leftSubject: (t) => `Hai lasciato ${t}`,
  leftBody: (t) => `La tua iscrizione a ${t} è annullata, come hai chiesto. L'organizzazione è avvisata.`,
  teamAddedSubject: (t, team) => `Sei nella squadra ${team} di ${t}`,
  teamAddedBody: (t, team, c) => `${c} ti ha aggiunto alla squadra ${team} a ${t}. Se è un errore, puoi lasciare la squadra con il link qui sotto.`,
  teamReminderSubject: (t) => `La tua squadra per ${t}`,
  teamReminderBody: (t, d) => `Le iscrizioni di ${t} chiudono il ${d} e non hai ancora una squadra completa (una squadra ha 4 giocatori). Forma la tua squadra o unisciti a una prima di quella data. Altrimenti sarà l'organizzazione a scegliere la tua squadra.`,
  orgLeftSubject: (t, p) => `Cancellazione a ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} ha annullato l'iscrizione a ${t}.`,
  orgCounts: (c, w) => `Ora: ${c} con un posto e ${w} in lista d'attesa.`,
  reportSubject: (t) => `Rapporto settimanale delle iscrizioni di ${t}`,
  reportIntro: (t) => `Le iscrizioni di ${t} di questa settimana.`,
  reportNew: "Nuove iscrizioni:",
  reportLeft: "Cancellazioni:",
  reportNone: "Nessuna.",
  reportNoTeam: (n) => `Giocatori senza squadra completa: ${n}.`,
  reportOpenAdmin: "Apri le iscrizioni",
  noEmaSubject: (t, p) => `Iscrizione senza numero EMA a ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} vuole iscriversi a ${t} e non ha un numero EMA.`,
  noEmaHint: "Aggiungi la persona nei giocatori EMA dell'app di gestione e poi aggiungi la sua iscrizione in Iscrizioni. Rispondi a questa email per scrivere alla persona.",
  noEmaMessage: "Messaggio:",
  sharedEmailNote: "Un'altra iscrizione a questo torneo ha questa stessa email. Va bene: un'email può iscrivere più persone, per esempio un genitore e un figlio. Ma un'email può avere un solo account nell'app: chi vuole un proprio account ha bisogno di una propria email. Molti provider, come Gmail, accettano un alias con +, per esempio nome+figlio@gmail.com: arriva nella stessa casella e conta come un'altra email.",
};

const de: Texts = {
  verifySubject: (t) => `Bestätige deine Anmeldung für ${t}`,
  verifyBody: (t, p) => `Öffne den Link unten, um die Anmeldung von ${p} für ${t} zu bestätigen. Der Link gilt 48 Stunden.`,
  verifyIgnore: "Wenn du das nicht angefordert hast, ignoriere diese E-Mail: Es ändert sich nichts.",
  verifyAction: "Anmeldung bestätigen",
  confirmedSubject: (t) => `Du bist für ${t} angemeldet`,
  confirmedBody: (t, p) => `${p}, deine Anmeldung für ${t} ist bestätigt. Du hast einen Platz.`,
  waitingSubject: (t) => `Du stehst auf der Warteliste von ${t}`,
  waitingBody: (t, p, n) => `${p}, die Plätze von ${t} sind voll. Du bist Nummer ${n} auf der Warteliste. Wir schreiben dir, wenn ein Platz für dich frei wird.`,
  manageIntro: "Mit diesem Link siehst du deine Anmeldung, bildest ein Team oder verlässt es, änderst deine Ernährungseinschränkungen oder meldest dich ab:",
  manageAction: "Meine Anmeldung verwalten",
  promotedSubject: (t) => `Du hast einen Platz bei ${t}`,
  promotedBody: (t, p) => `${p}, bei ${t} ist ein Platz frei geworden und er gehört dir. Deine Anmeldung ist bestätigt.`,
  removedSubject: (t) => `Deine Anmeldung für ${t} wurde storniert`,
  removedBody: (t, c) => `Die Organisation hat deine Anmeldung für ${t} storniert. Falls das ein Fehler ist, schreibe an ${c}.`,
  leftSubject: (t) => `Du hast dich von ${t} abgemeldet`,
  leftBody: (t) => `Deine Anmeldung für ${t} ist storniert, wie gewünscht. Die Organisation weiß Bescheid.`,
  teamAddedSubject: (t, team) => `Du bist im Team ${team} bei ${t}`,
  teamAddedBody: (t, team, c) => `${c} hat dich zum Team ${team} bei ${t} hinzugefügt. Falls das ein Fehler ist, kannst du das Team mit dem Link unten verlassen.`,
  teamReminderSubject: (t) => `Dein Team für ${t}`,
  teamReminderBody: (t, d) => `Die Anmeldung für ${t} schließt am ${d}, und du hast noch kein vollständiges Team (ein Team hat 4 Spieler). Bilde dein Team oder tritt einem bei, bevor es so weit ist. Sonst wählt die Organisation dein Team.`,
  orgLeftSubject: (t, p) => `Abmeldung bei ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} hat sich von ${t} abgemeldet.`,
  orgCounts: (c, w) => `Jetzt: ${c} mit Platz und ${w} auf der Warteliste.`,
  reportSubject: (t) => `Wochenbericht der Anmeldungen für ${t}`,
  reportIntro: (t) => `Die Anmeldungen für ${t} in dieser Woche.`,
  reportNew: "Neue Anmeldungen:",
  reportLeft: "Abmeldungen:",
  reportNone: "Keine.",
  reportNoTeam: (n) => `Spieler ohne vollständiges Team: ${n}.`,
  reportOpenAdmin: "Anmeldungen öffnen",
  noEmaSubject: (t, p) => `Anmeldung ohne EMA-Nummer für ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} möchte sich für ${t} anmelden und hat keine EMA-Nummer.`,
  noEmaHint: "Lege die Person in den EMA-Spielern der Verwaltungs-App an und füge dann ihre Anmeldung unter Anmeldungen hinzu. Antworte auf diese E-Mail, um der Person zu schreiben.",
  noEmaMessage: "Nachricht:",
  sharedEmailNote: "Eine andere Anmeldung für dieses Turnier hat dieselbe E-Mail. Das ist in Ordnung: Mit einer E-Mail kann man mehrere Personen anmelden, zum Beispiel ein Elternteil und ein Kind. Aber eine E-Mail kann nur ein Konto in der App haben: Wer ein eigenes Konto möchte, braucht eine eigene E-Mail. Viele Anbieter wie Gmail nehmen einen Alias mit +, zum Beispiel name+kind@gmail.com: Er landet im selben Postfach und zählt als andere E-Mail.",
};

const sv: Texts = {
  verifySubject: (t) => `Bekräfta din anmälan till ${t}`,
  verifyBody: (t, p) => `Öppna länken nedan för att bekräfta anmälan av ${p} till ${t}. Länken fungerar i 48 timmar.`,
  verifyIgnore: "Om du inte bad om det kan du strunta i det här mejlet: inget ändras.",
  verifyAction: "Bekräfta min anmälan",
  confirmedSubject: (t) => `Du är anmäld till ${t}`,
  confirmedBody: (t, p) => `${p}, din anmälan till ${t} är bekräftad. Du har en plats.`,
  waitingSubject: (t) => `Du står på väntelistan till ${t}`,
  waitingBody: (t, p, n) => `${p}, platserna i ${t} är fullbokade. Du är nummer ${n} på väntelistan. Vi skriver till dig när en plats blir din.`,
  manageIntro: "Med den här länken kan du se din anmälan, bilda ett lag eller lämna det, ändra dina kostrestriktioner eller avanmäla dig:",
  manageAction: "Hantera min anmälan",
  promotedSubject: (t) => `Du har en plats i ${t}`,
  promotedBody: (t, p) => `${p}, en plats har blivit ledig i ${t} och den är din. Din anmälan är bekräftad.`,
  removedSubject: (t) => `Din anmälan till ${t} har avbokats`,
  removedBody: (t, c) => `Arrangören har avbokat din anmälan till ${t}. Om det är fel, skriv till ${c}.`,
  leftSubject: (t) => `Du har lämnat ${t}`,
  leftBody: (t) => `Din anmälan till ${t} är avbokad, som du bad om. Arrangören vet om det.`,
  teamAddedSubject: (t, team) => `Du är med i laget ${team} i ${t}`,
  teamAddedBody: (t, team, c) => `${c} har lagt till dig i laget ${team} i ${t}. Om det är fel kan du lämna laget med länken nedan.`,
  teamReminderSubject: (t) => `Ditt lag till ${t}`,
  teamReminderBody: (t, d) => `Anmälan till ${t} stänger den ${d} och du har ännu inget fullt lag (ett lag har 4 spelare). Bilda ditt lag eller gå med i ett innan dess. Annars väljer arrangören ditt lag.`,
  orgLeftSubject: (t, p) => `Avanmälan i ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} har avanmält sig från ${t}.`,
  orgCounts: (c, w) => `Nu: ${c} med plats och ${w} på väntelistan.`,
  reportSubject: (t) => `Veckorapport för anmälningarna till ${t}`,
  reportIntro: (t) => `Anmälningarna till ${t} den här veckan.`,
  reportNew: "Nya anmälningar:",
  reportLeft: "Avanmälningar:",
  reportNone: "Inga.",
  reportNoTeam: (n) => `Spelare utan fullt lag: ${n}.`,
  reportOpenAdmin: "Öppna anmälningarna",
  noEmaSubject: (t, p) => `Anmälan utan EMA-nummer till ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} vill anmäla sig till ${t} och har inget EMA-nummer.`,
  noEmaHint: "Lägg till personen bland EMA-spelarna i administrationsappen och lägg sedan till anmälan under Anmälningar. Svara på det här mejlet för att skriva till personen.",
  noEmaMessage: "Meddelande:",
  sharedEmailNote: "En annan anmälan till den här turneringen har samma e-post. Det går bra: en e-post kan anmäla flera personer, till exempel en förälder och ett barn. Men en e-post kan bara ha ett konto i appen: den som vill ha ett eget konto behöver en egen e-post. Många leverantörer, som Gmail, tar ett alias med +, till exempel namn+barn@gmail.com: det kommer till samma inkorg och räknas som en annan e-post.",
};

const nl: Texts = {
  verifySubject: (t) => `Bevestig je inschrijving voor ${t}`,
  verifyBody: (t, p) => `Open de link hieronder om de inschrijving van ${p} voor ${t} te bevestigen. De link werkt 48 uur.`,
  verifyIgnore: "Heb je dit niet gevraagd? Negeer dan deze e-mail: er verandert niets.",
  verifyAction: "Mijn inschrijving bevestigen",
  confirmedSubject: (t) => `Je bent ingeschreven voor ${t}`,
  confirmedBody: (t, p) => `${p}, je inschrijving voor ${t} is bevestigd. Je hebt een plaats.`,
  waitingSubject: (t) => `Je staat op de wachtlijst van ${t}`,
  waitingBody: (t, p, n) => `${p}, de plaatsen van ${t} zijn vol. Je bent nummer ${n} op de wachtlijst. We schrijven je zodra er een plaats voor jou is.`,
  manageIntro: "Met deze link zie je je inschrijving, vorm je een team of verlaat je het, wijzig je je dieetwensen of schrijf je je uit:",
  manageAction: "Mijn inschrijving beheren",
  promotedSubject: (t) => `Je hebt een plaats in ${t}`,
  promotedBody: (t, p) => `${p}, er is een plaats vrijgekomen in ${t} en die is voor jou. Je inschrijving is bevestigd.`,
  removedSubject: (t) => `Je inschrijving voor ${t} is geannuleerd`,
  removedBody: (t, c) => `De organisatie heeft je inschrijving voor ${t} geannuleerd. Is dit een fout? Schrijf dan naar ${c}.`,
  leftSubject: (t) => `Je hebt je uitgeschreven voor ${t}`,
  leftBody: (t) => `Je inschrijving voor ${t} is geannuleerd, zoals je vroeg. De organisatie weet het.`,
  teamAddedSubject: (t, team) => `Je zit in het team ${team} van ${t}`,
  teamAddedBody: (t, team, c) => `${c} heeft je toegevoegd aan het team ${team} in ${t}. Is dit een fout? Dan kun je het team verlaten met de link hieronder.`,
  teamReminderSubject: (t) => `Je team voor ${t}`,
  teamReminderBody: (t, d) => `De inschrijving voor ${t} sluit op ${d} en je hebt nog geen volledig team (een team heeft 4 spelers). Vorm je team of sluit je bij een team aan voor die datum. Anders kiest de organisatie je team.`,
  orgLeftSubject: (t, p) => `Uitschrijving in ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} heeft zich uitgeschreven voor ${t}.`,
  orgCounts: (c, w) => `Nu: ${c} met een plaats en ${w} op de wachtlijst.`,
  reportSubject: (t) => `Weekverslag van de inschrijvingen voor ${t}`,
  reportIntro: (t) => `De inschrijvingen voor ${t} van deze week.`,
  reportNew: "Nieuwe inschrijvingen:",
  reportLeft: "Uitschrijvingen:",
  reportNone: "Geen.",
  reportNoTeam: (n) => `Spelers zonder volledig team: ${n}.`,
  reportOpenAdmin: "Inschrijvingen openen",
  noEmaSubject: (t, p) => `Inschrijving zonder EMA-nummer voor ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} wil zich inschrijven voor ${t} en heeft geen EMA-nummer.`,
  noEmaHint: "Voeg de persoon toe bij de EMA-spelers van de beheerapp en voeg daarna de inschrijving toe bij Inschrijvingen. Beantwoord deze e-mail om de persoon te schrijven.",
  noEmaMessage: "Bericht:",
  sharedEmailNote: "Een andere inschrijving voor dit toernooi heeft hetzelfde e-mailadres. Dat is geen probleem: met één e-mailadres kun je meerdere mensen inschrijven, bijvoorbeeld een ouder en een kind. Maar één e-mailadres kan maar één account in de app hebben: wie een eigen account wil, heeft een eigen e-mailadres nodig. Veel providers, zoals Gmail, accepteren een alias met +, bijvoorbeeld naam+kind@gmail.com: die komt in dezelfde inbox en telt als een ander e-mailadres.",
};

const ru: Texts = {
  verifySubject: (t) => `Подтвердите регистрацию на ${t}`,
  verifyBody: (t, p) => `Откройте ссылку ниже, чтобы подтвердить регистрацию ${p} на ${t}. Ссылка действует 48 часов.`,
  verifyIgnore: "Если вы этого не запрашивали, просто проигнорируйте письмо: ничего не изменится.",
  verifyAction: "Подтвердить регистрацию",
  confirmedSubject: (t) => `Вы зарегистрированы на ${t}`,
  confirmedBody: (t, p) => `${p}, ваша регистрация на ${t} подтверждена. У вас есть место.`,
  waitingSubject: (t) => `Вы в листе ожидания ${t}`,
  waitingBody: (t, p, n) => `${p}, все места на ${t} заняты. Вы номер ${n} в листе ожидания. Мы напишем вам, когда место станет вашим.`,
  manageIntro: "По этой ссылке вы можете посмотреть регистрацию, создать команду или выйти из неё, изменить пищевые ограничения или отменить регистрацию:",
  manageAction: "Управлять регистрацией",
  promotedSubject: (t) => `У вас есть место на ${t}`,
  promotedBody: (t, p) => `${p}, на ${t} освободилось место, и оно ваше. Ваша регистрация подтверждена.`,
  removedSubject: (t) => `Ваша регистрация на ${t} отменена`,
  removedBody: (t, c) => `Организаторы отменили вашу регистрацию на ${t}. Если это ошибка, напишите на ${c}.`,
  leftSubject: (t) => `Вы отменили участие в ${t}`,
  leftBody: (t) => `Ваша регистрация на ${t} отменена, как вы просили. Организаторы знают об этом.`,
  teamAddedSubject: (t, team) => `Вы в команде ${team} на ${t}`,
  teamAddedBody: (t, team, c) => `${c} добавил(а) вас в команду ${team} на ${t}. Если это ошибка, вы можете выйти из команды по ссылке ниже.`,
  teamReminderSubject: (t) => `Ваша команда на ${t}`,
  teamReminderBody: (t, d) => `Регистрация на ${t} закрывается ${d}, а у вас ещё нет полной команды (в команде 4 игрока). Создайте команду или присоединитесь к одной до этой даты. Иначе команду выберут организаторы.`,
  orgLeftSubject: (t, p) => `Отмена регистрации на ${t}: ${p}`,
  orgLeftBody: (t, p) => `${p} отменил(а) регистрацию на ${t}.`,
  orgCounts: (c, w) => `Сейчас: ${c} с местом и ${w} в листе ожидания.`,
  reportSubject: (t) => `Еженедельный отчёт о регистрации на ${t}`,
  reportIntro: (t) => `Регистрация на ${t} за эту неделю.`,
  reportNew: "Новые регистрации:",
  reportLeft: "Отмены:",
  reportNone: "Нет.",
  reportNoTeam: (n) => `Игроков без полной команды: ${n}.`,
  reportOpenAdmin: "Открыть регистрацию",
  noEmaSubject: (t, p) => `Регистрация без номера EMA на ${t}: ${p}`,
  noEmaBody: (t, p) => `${p} хочет зарегистрироваться на ${t}, но у него/неё нет номера EMA.`,
  noEmaHint: "Добавьте человека в игроков EMA в приложении управления, а затем добавьте его регистрацию в разделе «Регистрация». Ответьте на это письмо, чтобы написать человеку.",
  noEmaMessage: "Сообщение:",
  sharedEmailNote: "У другой регистрации на этот турнир тот же email. Это нормально: с одного email можно зарегистрировать нескольких человек, например родителя и ребёнка. Но у одного email может быть только один аккаунт в приложении: кому нужен свой аккаунт, нужен свой email. Многие почтовые сервисы, например Gmail, принимают адрес с +, например name+child@gmail.com: письма приходят в тот же ящик, а адрес считается другим.",
};

const TEXTS: Record<Language, Texts> = { en, es, fr, it, de, sv, nl, ru };

/** The language of the organization: the main language of the host country of the tournament, English otherwise. */
export function organizationLanguage(hostCountry: string): Language {
  const byCountry: Record<string, Language> = {
    ES: "es", AR: "es", MX: "es", CL: "es", FR: "fr", BE: "fr", IT: "it", DE: "de", AT: "de", CH: "de",
    SE: "sv", NL: "nl", RU: "ru",
  };
  return byCountry[hostCountry.trim().toUpperCase()] ?? "en";
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function build(email: Paragraphs): BuiltRegistrationEmail {
  const lines = email.lines ?? [];
  const textParts = [...email.paragraphs];
  if (lines.length > 0) textParts.push(lines.join("\n"));
  if (email.action) textParts.push(`${email.action.label}: ${email.action.link}`);
  const html = email.paragraphs.map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, "<br>")}</p>`).join("")
    + (lines.length > 0 ? `<p>${lines.map(escapeHtml).join("<br>")}</p>` : "")
    + (email.action ? `<p><a href="${escapeHtml(email.action.link)}">${escapeHtml(email.action.label)}</a></p>` : "");
  // eslint-disable-next-line no-control-regex
  return { subject: email.subject.replace(/[\u0000-\u001f\u007f]+/g, " ").trim(), text: textParts.join("\n\n"), html };
}

export function verifyEmail(language: Language, p: { tournament: string; person: string; link: string; sharedEmail?: boolean }): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({
    subject: t.verifySubject(p.tournament),
    paragraphs: [t.verifyBody(p.tournament, p.person), ...(p.sharedEmail ? [t.sharedEmailNote] : []), t.verifyIgnore],
    action: { label: t.verifyAction, link: p.link },
  });
}

export function registeredEmail(
  language: Language,
  p: { tournament: string; person: string; link: string; waitingPosition: number | null; sharedEmail?: boolean },
): BuiltRegistrationEmail {
  const t = TEXTS[language];
  const waiting = p.waitingPosition != null;
  return build({
    subject: waiting ? t.waitingSubject(p.tournament) : t.confirmedSubject(p.tournament),
    paragraphs: [
      waiting ? t.waitingBody(p.tournament, p.person, p.waitingPosition ?? 0) : t.confirmedBody(p.tournament, p.person),
      ...(p.sharedEmail ? [t.sharedEmailNote] : []),
      t.manageIntro,
    ],
    action: { label: t.manageAction, link: p.link },
  });
}

export function promotedEmail(language: Language, p: { tournament: string; person: string; link: string }): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({
    subject: t.promotedSubject(p.tournament),
    paragraphs: [t.promotedBody(p.tournament, p.person), t.manageIntro],
    action: { label: t.manageAction, link: p.link },
  });
}

export function removedEmail(language: Language, p: { tournament: string; contact: string }): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({ subject: t.removedSubject(p.tournament), paragraphs: [t.removedBody(p.tournament, p.contact)] });
}

export function leftEmail(language: Language, p: { tournament: string }): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({ subject: t.leftSubject(p.tournament), paragraphs: [t.leftBody(p.tournament)] });
}

export function teamAddedEmail(
  language: Language,
  p: { tournament: string; team: string; captain: string; link: string },
): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({
    subject: t.teamAddedSubject(p.tournament, p.team),
    paragraphs: [t.teamAddedBody(p.tournament, p.team, p.captain)],
    action: { label: t.manageAction, link: p.link },
  });
}

export function teamReminderEmail(language: Language, p: { tournament: string; closesOn: string; link: string }): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({
    subject: t.teamReminderSubject(p.tournament),
    paragraphs: [t.teamReminderBody(p.tournament, p.closesOn)],
    action: { label: t.manageAction, link: p.link },
  });
}

export function organizationLeftEmail(
  language: Language,
  p: { tournament: string; person: string; confirmed: number; waiting: number },
): BuiltRegistrationEmail {
  const t = TEXTS[language];
  return build({
    subject: t.orgLeftSubject(p.tournament, p.person),
    paragraphs: [t.orgLeftBody(p.tournament, p.person), t.orgCounts(p.confirmed, p.waiting)],
  });
}

export function weeklyReportEmail(
  language: Language,
  p: { tournament: string; registered: string[]; left: string[]; confirmed: number; waiting: number; withoutTeam: number | null; link: string },
): BuiltRegistrationEmail {
  const t = TEXTS[language];
  const list = (items: string[]) => (items.length === 0 ? t.reportNone : items.map((item) => `• ${item}`).join("\n"));
  const paragraphs = [
    t.reportIntro(p.tournament),
    `${t.reportNew}\n${list(p.registered)}`,
    `${t.reportLeft}\n${list(p.left)}`,
    t.orgCounts(p.confirmed, p.waiting),
  ];
  if (p.withoutTeam != null) paragraphs.push(t.reportNoTeam(p.withoutTeam));
  return build({ subject: t.reportSubject(p.tournament), paragraphs, action: { label: t.reportOpenAdmin, link: p.link } });
}

export function noEmaNumberEmail(
  language: Language,
  p: { tournament: string; person: string; message: string },
): BuiltRegistrationEmail {
  const t = TEXTS[language];
  const paragraphs = [t.noEmaBody(p.tournament, p.person)];
  if (p.message.trim() !== "") paragraphs.push(`${t.noEmaMessage}\n${p.message.trim()}`);
  paragraphs.push(t.noEmaHint);
  return build({ subject: t.noEmaSubject(p.tournament, p.person), paragraphs });
}
