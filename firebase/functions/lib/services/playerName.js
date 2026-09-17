"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.decodePlayerNameEntities = decodePlayerNameEntities;
exports.normalizePlayerName = normalizePlayerName;
const namedHtmlEntities = {
    amp: "&",
    apos: "'",
    acute: "´",
    agrave: "à",
    aacute: "á",
    acirc: "â",
    atilde: "ã",
    auml: "ä",
    aring: "å",
    aelig: "æ",
    cacute: "ć",
    ccaron: "č",
    ccedil: "ç",
    dcaron: "ď",
    eth: "ð",
    egrave: "è",
    eacute: "é",
    ecirc: "ê",
    euml: "ë",
    ecaron: "ě",
    igrave: "ì",
    iacute: "í",
    icirc: "î",
    iuml: "ï",
    lacute: "ĺ",
    lcaron: "ľ",
    lstrok: "ł",
    nacute: "ń",
    ncaron: "ň",
    ntilde: "ñ",
    ograve: "ò",
    oacute: "ó",
    ocirc: "ô",
    otilde: "õ",
    ouml: "ö",
    odblac: "ő",
    oelig: "œ",
    oslash: "ø",
    racute: "ŕ",
    rcaron: "ř",
    sacute: "ś",
    scaron: "š",
    szlig: "ß",
    tcaron: "ť",
    thorn: "þ",
    ugrave: "ù",
    uacute: "ú",
    ucirc: "û",
    uuml: "ü",
    udblac: "ű",
    uring: "ů",
    yacute: "ý",
    yuml: "ÿ",
    zcaron: "ž",
    nbsp: " ",
    quot: "\"",
    lt: "<",
    gt: ">",
};
/** Decode the named and numeric entities used by EMA player names. */
function decodePlayerNameEntities(value) {
    return value.replace(/&(#x[0-9a-f]+|#\d+|[a-z][a-z0-9]+);/gi, (entity, code) => {
        if (code.toLowerCase().startsWith("#x")) {
            const parsed = Number.parseInt(code.slice(2), 16);
            return Number.isFinite(parsed) ? String.fromCodePoint(parsed) : entity;
        }
        if (code.startsWith("#")) {
            const parsed = Number.parseInt(code.slice(1), 10);
            return Number.isFinite(parsed) ? String.fromCodePoint(parsed) : entity;
        }
        return namedHtmlEntities[code.toLowerCase()] ?? entity;
    });
}
/** Keep player names in one canonical Unicode form and uppercase every supported letter. */
function normalizePlayerName(value) {
    return decodePlayerNameEntities(value).normalize("NFC").toUpperCase();
}
//# sourceMappingURL=playerName.js.map