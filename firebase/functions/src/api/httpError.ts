export class HttpError extends Error {
  status: number;
  code: string;
  details?: unknown;

  constructor(status: number, code: string, message: string, details?: unknown) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function badRequest(message: string, details?: unknown): HttpError {
  return new HttpError(400, "bad_request", message, details);
}

export function unauthenticated(message = "Unauthenticated"): HttpError {
  return new HttpError(401, "unauthenticated", message);
}

export function forbidden(message = "Forbidden"): HttpError {
  return new HttpError(403, "forbidden", message);
}

export function notFound(message = "Not found"): HttpError {
  return new HttpError(404, "not_found", message);
}

export function conflict(message: string): HttpError {
  return new HttpError(409, "conflict", message);
}
