import type { Language } from "./i18n";
import { sendEmail } from "./mailService";

/**
 * The emails of a change of the number of players (docs/player-count-change.md), in the eight languages of the apps.
 * Every person affected gets one: the players who stay (new tables, maybe a new player number), the people who
 * leave, and the people of the waiting list who get a place.
 */
export type BuiltPlayerCountEmail = { subject: string; text: string; html: string };

type Texts = {
  staySubject: (t: string) => string;
  stayBody: (t: string, from: number, to: number) => string;
  stayNumber: (n: number) => string;
  stayNewNumber: (old: number, n: number) => string;
  stayAction: string;
  leaveSubject: (t: string) => string;
  leaveBody: (t: string, from: number, to: number) => string;
  leaveContact: (c: string) => string;
  placeSubject: (t: string) => string;
  placeBody: (t: string, from: number, to: number, n: number) => string;
};

const en: Texts = {
  staySubject: (t) => `New tables in ${t}`,
  stayBody: (t, from, to) => `The organization changed the number of players of ${t} from ${from} to ${to}, and the tables of every round were drawn again.`,
  stayNumber: (n) => `Your player number is still ${n}. Your new tables are in the app.`,
  stayNewNumber: (old, n) => `Your player number changes from ${old} to ${n}. Your new tables are in the app.`,
  stayAction: "See my tables",
  leaveSubject: (t) => `You no longer play in ${t}`,
  leaveBody: (t, from, to) => `The organization changed the number of players of ${t} from ${from} to ${to}, and your place is no longer in the tournament.`,
  leaveContact: (c) => `If this is a mistake, write to ${c}.`,
  placeSubject: (t) => `You have a place in ${t}`,
  placeBody: (t, from, to, n) => `The organization changed the number of players of ${t} from ${from} to ${to}, and one of the new places is yours. Your player number is ${n}. Your tables are in the app.`,
};

const es: Texts = {
  staySubject: (t) => `Mesas nuevas en ${t}`,
  stayBody: (t, from, to) => `La organización ha cambiado el número de jugadores de ${t} de ${from} a ${to}, y se han sorteado de nuevo las mesas de todas las rondas.`,
  stayNumber: (n) => `Tu número de jugador sigue siendo el ${n}. Tus mesas nuevas están en la app.`,
  stayNewNumber: (old, n) => `Tu número de jugador pasa del ${old} al ${n}. Tus mesas nuevas están en la app.`,
  stayAction: "Ver mis mesas",
  leaveSubject: (t) => `Ya no juegas en ${t}`,
  leaveBody: (t, from, to) => `La organización ha cambiado el número de jugadores de ${t} de ${from} a ${to}, y tu plaza ya no está en el torneo.`,
  leaveContact: (c) => `Si es un error, escribe a ${c}.`,
  placeSubject: (t) => `Tienes plaza en ${t}`,
  placeBody: (t, from, to, n) => `La organización ha cambiado el número de jugadores de ${t} de ${from} a ${to}, y una de las plazas nuevas es tuya. Tu número de jugador es el ${n}. Tus mesas están en la app.`,
};

const fr: Texts = {
  staySubject: (t) => `Nouvelles tables à ${t}`,
  stayBody: (t, from, to) => `L'organisation a changé le nombre de joueurs de ${t} de ${from} à ${to}, et les tables de toutes les rondes ont été tirées à nouveau.`,
  stayNumber: (n) => `Votre numéro de joueur reste le ${n}. Vos nouvelles tables sont dans l'app.`,
  stayNewNumber: (old, n) => `Votre numéro de joueur passe du ${old} au ${n}. Vos nouvelles tables sont dans l'app.`,
  stayAction: "Voir mes tables",
  leaveSubject: (t) => `Vous ne jouez plus à ${t}`,
  leaveBody: (t, from, to) => `L'organisation a changé le nombre de joueurs de ${t} de ${from} à ${to}, et votre place ne fait plus partie du tournoi.`,
  leaveContact: (c) => `S'il s'agit d'une erreur, écrivez à ${c}.`,
  placeSubject: (t) => `Vous avez une place à ${t}`,
  placeBody: (t, from, to, n) => `L'organisation a changé le nombre de joueurs de ${t} de ${from} à ${to}, et l'une des nouvelles places est à vous. Votre numéro de joueur est le ${n}. Vos tables sont dans l'app.`,
};

const it: Texts = {
  staySubject: (t) => `Nuovi tavoli a ${t}`,
  stayBody: (t, from, to) => `L'organizzazione ha cambiato il numero di giocatori di ${t} da ${from} a ${to}, e i tavoli di tutti i turni sono stati sorteggiati di nuovo.`,
  stayNumber: (n) => `Il tuo numero di giocatore resta il ${n}. I tuoi nuovi tavoli sono nell'app.`,
  stayNewNumber: (old, n) => `Il tuo numero di giocatore passa dal ${old} al ${n}. I tuoi nuovi tavoli sono nell'app.`,
  stayAction: "Vedi i miei tavoli",
  leaveSubject: (t) => `Non giochi più a ${t}`,
  leaveBody: (t, from, to) => `L'organizzazione ha cambiato il numero di giocatori di ${t} da ${from} a ${to}, e il tuo posto non è più nel torneo.`,
  leaveContact: (c) => `Se è un errore, scrivi a ${c}.`,
  placeSubject: (t) => `Hai un posto a ${t}`,
  placeBody: (t, from, to, n) => `L'organizzazione ha cambiato il numero di giocatori di ${t} da ${from} a ${to}, e uno dei nuovi posti è tuo. Il tuo numero di giocatore è il ${n}. I tuoi tavoli sono nell'app.`,
};

const de: Texts = {
  staySubject: (t) => `Neue Tische bei ${t}`,
  stayBody: (t, from, to) => `Die Organisation hat die Zahl der Spieler von ${t} von ${from} auf ${to} geändert, und die Tische aller Runden wurden neu ausgelost.`,
  stayNumber: (n) => `Deine Spielernummer bleibt ${n}. Deine neuen Tische findest du in der App.`,
  stayNewNumber: (old, n) => `Deine Spielernummer ändert sich von ${old} auf ${n}. Deine neuen Tische findest du in der App.`,
  stayAction: "Meine Tische ansehen",
  leaveSubject: (t) => `Du spielst nicht mehr bei ${t}`,
  leaveBody: (t, from, to) => `Die Organisation hat die Zahl der Spieler von ${t} von ${from} auf ${to} geändert, und dein Platz ist nicht mehr im Turnier.`,
  leaveContact: (c) => `Wenn das ein Fehler ist, schreib an ${c}.`,
  placeSubject: (t) => `Du hast einen Platz bei ${t}`,
  placeBody: (t, from, to, n) => `Die Organisation hat die Zahl der Spieler von ${t} von ${from} auf ${to} geändert, und einer der neuen Plätze gehört dir. Deine Spielernummer ist ${n}. Deine Tische findest du in der App.`,
};

const sv: Texts = {
  staySubject: (t) => `Nya bord i ${t}`,
  stayBody: (t, from, to) => `Organisationen har ändrat antalet spelare i ${t} från ${from} till ${to}, och borden i alla omgångar har lottats på nytt.`,
  stayNumber: (n) => `Ditt spelarnummer är fortfarande ${n}. Dina nya bord finns i appen.`,
  stayNewNumber: (old, n) => `Ditt spelarnummer ändras från ${old} till ${n}. Dina nya bord finns i appen.`,
  stayAction: "Se mina bord",
  leaveSubject: (t) => `Du spelar inte längre i ${t}`,
  leaveBody: (t, from, to) => `Organisationen har ändrat antalet spelare i ${t} från ${from} till ${to}, och din plats finns inte längre i turneringen.`,
  leaveContact: (c) => `Om det är ett misstag, skriv till ${c}.`,
  placeSubject: (t) => `Du har en plats i ${t}`,
  placeBody: (t, from, to, n) => `Organisationen har ändrat antalet spelare i ${t} från ${from} till ${to}, och en av de nya platserna är din. Ditt spelarnummer är ${n}. Dina bord finns i appen.`,
};

const nl: Texts = {
  staySubject: (t) => `Nieuwe tafels in ${t}`,
  stayBody: (t, from, to) => `De organisatie heeft het aantal spelers van ${t} veranderd van ${from} naar ${to}, en de tafels van alle rondes zijn opnieuw geloot.`,
  stayNumber: (n) => `Je spelersnummer blijft ${n}. Je nieuwe tafels staan in de app.`,
  stayNewNumber: (old, n) => `Je spelersnummer verandert van ${old} naar ${n}. Je nieuwe tafels staan in de app.`,
  stayAction: "Mijn tafels bekijken",
  leaveSubject: (t) => `Je speelt niet meer in ${t}`,
  leaveBody: (t, from, to) => `De organisatie heeft het aantal spelers van ${t} veranderd van ${from} naar ${to}, en je plaats hoort niet meer bij het toernooi.`,
  leaveContact: (c) => `Als dit een vergissing is, schrijf naar ${c}.`,
  placeSubject: (t) => `Je hebt een plaats in ${t}`,
  placeBody: (t, from, to, n) => `De organisatie heeft het aantal spelers van ${t} veranderd van ${from} naar ${to}, en een van de nieuwe plaatsen is van jou. Je spelersnummer is ${n}. Je tafels staan in de app.`,
};

const ru: Texts = {
  staySubject: (t) => `Новые столы в ${t}`,
  stayBody: (t, from, to) => `Организаторы изменили число игроков турнира ${t} с ${from} на ${to}, и столы всех раундов разыграны заново.`,
  stayNumber: (n) => `Ваш номер игрока остаётся ${n}. Ваши новые столы — в приложении.`,
  stayNewNumber: (old, n) => `Ваш номер игрока меняется с ${old} на ${n}. Ваши новые столы — в приложении.`,
  stayAction: "Посмотреть мои столы",
  leaveSubject: (t) => `Вы больше не играете в ${t}`,
  leaveBody: (t, from, to) => `Организаторы изменили число игроков турнира ${t} с ${from} на ${to}, и вашего места в турнире больше нет.`,
  leaveContact: (c) => `Если это ошибка, напишите на ${c}.`,
  placeSubject: (t) => `У вас есть место в ${t}`,
  placeBody: (t, from, to, n) => `Организаторы изменили число игроков турнира ${t} с ${from} на ${to}, и одно из новых мест — ваше. Ваш номер игрока: ${n}. Ваши столы — в приложении.`,
};

const TEXTS: Record<Language, Texts> = { en, es, fr, it, de, sv, nl, ru };

function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function build(subject: string, paragraphs: string[], action?: { label: string; link: string }): BuiltPlayerCountEmail {
  const text = [...paragraphs, ...(action ? [`${action.label}: ${action.link}`] : [])].join("\n\n");
  const html = paragraphs.map((paragraph) => `<p>${escapeHtml(paragraph)}</p>`).join("")
    + (action ? `<p><a href="${escapeHtml(action.link)}">${escapeHtml(action.label)}</a></p>` : "");
  // eslint-disable-next-line no-control-regex
  return { subject: subject.replace(/[\u0000-\u001f\u007f]+/g, " ").trim(), text, html };
}

type Change = { tournament: string; from: number; to: number; link: string };

/** A player who stays: new tables, and the player number, which changes when a slot with a lower number left. */
export function stayEmail(language: Language, p: Change & { oldNumber: number; number: number }): BuiltPlayerCountEmail {
  const t = TEXTS[language];
  return build(t.staySubject(p.tournament), [
    t.stayBody(p.tournament, p.from, p.to),
    p.oldNumber === p.number ? t.stayNumber(p.number) : t.stayNewNumber(p.oldNumber, p.number),
  ], { label: t.stayAction, link: p.link });
}

/** A person who leaves the tournament. */
export function leaveEmail(language: Language, p: { tournament: string; from: number; to: number; contact: string }): BuiltPlayerCountEmail {
  const t = TEXTS[language];
  return build(t.leaveSubject(p.tournament), [t.leaveBody(p.tournament, p.from, p.to), ...(p.contact === "" ? [] : [t.leaveContact(p.contact)])]);
}

/** A person of the waiting list who gets one of the new places. */
export function placeEmail(language: Language, p: Change & { number: number }): BuiltPlayerCountEmail {
  const t = TEXTS[language];
  return build(t.placeSubject(p.tournament), [t.placeBody(p.tournament, p.from, p.to, p.number)], { label: t.stayAction, link: p.link });
}

/** One person to tell, and the email that fits. A null address is a person the organizer must tell. */
export type PlayerCountRecipient = { emaId: string; to: string | null; email: BuiltPlayerCountEmail };

/** What the emails gave: the EMA ids of the people who got none, because they have no address or the send failed. */
export type PlayerCountEmailsResult = { sent: number; noEmail: string[]; failed: string[] };

/** Sends the emails, a few at a time. A failed email never undoes the change: the organizer sees who did not get it. */
export async function sendPlayerCountEmails(recipients: PlayerCountRecipient[], replyTo?: string): Promise<PlayerCountEmailsResult> {
  const result: PlayerCountEmailsResult = { sent: 0, noEmail: [], failed: [] };
  const queue = [...recipients];
  const worker = async () => {
    for (let next = queue.shift(); next != null; next = queue.shift()) {
      if (next.to == null || next.to === "") {
        result.noEmail.push(next.emaId);
        continue;
      }
      try {
        await sendEmail({ to: next.to, ...next.email, replyTo: replyTo || undefined });
        result.sent += 1;
      } catch (error) {
        console.error("A player count email failed", { emaId: next.emaId, error });
        result.failed.push(next.emaId);
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(5, recipients.length) }, worker));
  result.noEmail.sort();
  result.failed.sort();
  return result;
}
