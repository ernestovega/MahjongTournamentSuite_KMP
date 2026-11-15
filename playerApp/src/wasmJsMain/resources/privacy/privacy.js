// Opens the section in the language of the browser, unless the link already points to a section.
document.addEventListener("DOMContentLoaded", function () {
  if (location.hash) return;
  var langs = navigator.languages || [navigator.language || "en"];
  for (var i = 0; i < langs.length; i++) {
    var section = document.getElementById(String(langs[i]).slice(0, 2).toLowerCase());
    if (section && section.tagName === "SECTION") {
      if (section.id !== "en") section.scrollIntoView();
      return;
    }
  }
});
