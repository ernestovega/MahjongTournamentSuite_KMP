/**
 * The heavy libraries load on their first use and not when the function starts. Most requests never need them
 * (files, PDFs, emails), and a new instance of the API answers its first request sooner without them.
 * Node keeps a loaded module, so only the first call pays the load.
 */

export function excelJs(): typeof import("exceljs") {
  return require("exceljs");
}

export function jsZip(): typeof import("jszip") {
  return require("jszip");
}

export function sharp(): typeof import("sharp") {
  return require("sharp");
}

export function pdfKit(): typeof import("pdfkit") {
  return require("pdfkit");
}

export function nodemailer(): typeof import("nodemailer") {
  return require("nodemailer");
}
