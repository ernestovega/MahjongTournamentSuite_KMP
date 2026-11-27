import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { decodePlayerNameEntities, normalizePlayerNamePart } from "./playerName";

describe("names of the EMA players", () => {
  it("decodes named, decimal and hexadecimal entities, keeping the case of a named one", () => {
    assert.equal(decodePlayerNameEntities("K&Ouml;STERS &#233;l&#xE9;"), "KÖSTERS élé");
    assert.equal(decodePlayerNameEntities("&ouml;"), "ö");
  });

  it("keeps an entity that it does not know or whose number is no letter", () => {
    assert.equal(decodePlayerNameEntities("&unknown; &#x110000; &#9999999;"), "&unknown; &#x110000; &#9999999;");
  });

  it("joins the spaces and trims", () => {
    assert.equal(normalizePlayerNamePart("  Ana&nbsp;  María "), "Ana María");
  });
});
