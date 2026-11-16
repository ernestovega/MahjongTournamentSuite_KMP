/** The languages of the emails. English is the base and the fallback. */
export const LANGUAGES = ["en", "es", "fr", "it", "de", "sv", "nl", "ru"] as const;
export type Language = typeof LANGUAGES[number];
export const DEFAULT_LANGUAGE: Language = "en";

/**
 * Picks the best supported language of an `Accept-Language` header.
 * The languages are tried by their quality value. An unknown or missing header gives English.
 */
export function parseAcceptLanguage(header: string | string[] | undefined | null): Language {
  const value = Array.isArray(header) ? header.join(",") : header;
  if (!value) return DEFAULT_LANGUAGE;
  const candidates = value.split(",").map((part, index) => {
    const [tag, ...params] = part.trim().split(";");
    const q = params.map((param) => param.trim()).find((param) => param.startsWith("q="));
    const quality = q ? Number(q.slice(2)) : 1;
    return { tag: tag.trim().toLowerCase(), quality: Number.isFinite(quality) ? quality : 0, index };
  }).filter((candidate) => candidate.tag.length > 0 && candidate.quality > 0)
    .sort((a, b) => b.quality - a.quality || a.index - b.index);
  for (const candidate of candidates) {
    const primary = candidate.tag.split("-")[0];
    const match = LANGUAGES.find((language) => language === primary);
    if (match) return match;
  }
  return DEFAULT_LANGUAGE;
}

export type EmailTexts = {
  appDescription: string;
  registrationSubject: (tournamentName: string) => string;
  registrationIntro: (tournamentName: string) => string;
  registrationAction: string;
  registrationOpen: (link: string) => string;
};

const english: EmailTexts = {
  appDescription: "This is the player app of the tournament. "
    + "After you sign in, you can see where you play in each round, the ranking after every round, "
    + "and your own results. You can also see upcoming and past tournaments, your statistics, and more.",
  registrationSubject: (name) => `You are registered in ${name}`,
  registrationIntro: (name) => `You have been added to the tournament "${name}".`,
  registrationAction: "Open the tournament",
  registrationOpen: (link) => `Open the tournament: ${link}`,
};

const spanish: EmailTexts = {
  appDescription: "Esta es la app del jugador del torneo. "
    + "Después de iniciar sesión, puedes ver dónde juegas en cada ronda, la clasificación tras cada ronda "
    + "y tus propios resultados. También puedes ver los torneos próximos y pasados, tus estadísticas y más.",
  registrationSubject: (name) => `Estás inscrito en ${name}`,
  registrationIntro: (name) => `Te han añadido al torneo «${name}».`,
  registrationAction: "Abrir el torneo",
  registrationOpen: (link) => `Abrir el torneo: ${link}`,
};

const french: EmailTexts = {
  appDescription: "Voici l'app joueur du tournoi. "
    + "Après votre connexion, vous voyez où vous jouez à chaque tour, le classement après chaque tour "
    + "et vos propres résultats. Vous voyez aussi les tournois à venir et passés, vos statistiques et plus encore.",
  registrationSubject: (name) => `Vous êtes inscrit à ${name}`,
  registrationIntro: (name) => `Vous avez été ajouté au tournoi «${name}».`,
  registrationAction: "Ouvrir le tournoi",
  registrationOpen: (link) => `Ouvrir le tournoi : ${link}`,
};

const italian: EmailTexts = {
  appDescription: "Questa è l'app del giocatore del torneo. "
    + "Dopo l'accesso, puoi vedere dove giochi in ogni turno, la classifica dopo ogni turno "
    + "e i tuoi risultati. Puoi anche vedere i tornei in arrivo e passati, le tue statistiche e altro.",
  registrationSubject: (name) => `Sei iscritto a ${name}`,
  registrationIntro: (name) => `Sei stato aggiunto al torneo «${name}».`,
  registrationAction: "Apri il torneo",
  registrationOpen: (link) => `Apri il torneo: ${link}`,
};

const german: EmailTexts = {
  appDescription: "Das ist die Spieler-App des Turniers. "
    + "Nach der Anmeldung siehst du, wo du in jeder Runde spielst, die Rangliste nach jeder Runde "
    + "und deine eigenen Ergebnisse. Du siehst auch kommende und vergangene Turniere, deine Statistik und mehr.",
  registrationSubject: (name) => `Du bist für ${name} angemeldet`,
  registrationIntro: (name) => `Du wurdest zum Turnier «${name}» hinzugefügt.`,
  registrationAction: "Turnier öffnen",
  registrationOpen: (link) => `Turnier öffnen: ${link}`,
};

const swedish: EmailTexts = {
  appDescription: "Det här är turneringens spelarapp. "
    + "När du har loggat in ser du var du spelar i varje omgång, rankingen efter varje omgång "
    + "och dina egna resultat. Du ser också kommande och tidigare turneringar, din statistik och mer.",
  registrationSubject: (name) => `Du är anmäld till ${name}`,
  registrationIntro: (name) => `Du har lagts till i turneringen "${name}".`,
  registrationAction: "Öppna turneringen",
  registrationOpen: (link) => `Öppna turneringen: ${link}`,
};

const dutch: EmailTexts = {
  appDescription: "Dit is de spelersapp van het toernooi. "
    + "Na het aanmelden zie je waar je in elke ronde speelt, het klassement na elke ronde "
    + "en je eigen resultaten. Je ziet ook komende en afgelopen toernooien, je statistieken en meer.",
  registrationSubject: (name) => `Je bent ingeschreven voor ${name}`,
  registrationIntro: (name) => `Je bent toegevoegd aan het toernooi "${name}".`,
  registrationAction: "Toernooi openen",
  registrationOpen: (link) => `Toernooi openen: ${link}`,
};

const russian: EmailTexts = {
  appDescription: "Это приложение игрока турнира. "
    + "После входа вы видите, где играете в каждом раунде, рейтинг после каждого раунда "
    + "и свои результаты. Также вы видите предстоящие и прошедшие турниры, свою статистику и многое другое.",
  registrationSubject: (name) => `Вы зарегистрированы на турнир ${name}`,
  registrationIntro: (name) => `Вас добавили в турнир «${name}».`,
  registrationAction: "Открыть турнир",
  registrationOpen: (link) => `Открыть турнир: ${link}`,
};

const TEXTS: Record<Language, EmailTexts> = {
  en: english, es: spanish, fr: french, it: italian, de: german, sv: swedish, nl: dutch, ru: russian,
};

export function emailTexts(language: Language): EmailTexts {
  return TEXTS[language];
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export type BuiltEmail = { subject: string; text: string; html: string };

export function buildRegistrationEmail(
  language: Language,
  params: { tournamentName: string; link: string },
): BuiltEmail {
  const t = emailTexts(language);
  return {
    subject: t.registrationSubject(params.tournamentName),
    text: `${t.registrationIntro(params.tournamentName)}\n\n${t.appDescription}\n\n${t.registrationOpen(params.link)}`,
    html: `<p>${escapeHtml(t.registrationIntro(params.tournamentName)).replace(
      escapeHtml(`"${params.tournamentName}"`), `<strong>${escapeHtml(params.tournamentName)}</strong>`)
      .replace(escapeHtml(`«${params.tournamentName}»`), `<strong>${escapeHtml(params.tournamentName)}</strong>`)}</p>`
      + `<p>${t.appDescription}</p>`
      + `<p><a href="${params.link}">${t.registrationAction}</a></p>`,
  };
}
