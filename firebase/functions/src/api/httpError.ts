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

export function conflict(message: string, details?: unknown): HttpError {
  return new HttpError(409, "conflict", message, details);
}

/**
 * Stops ids that could leave their place in a document path. Express decodes `%2F` to a slash in route parameters,
 * so an id from a URL or an invitation token must not contain one.
 */
export function assertSafeDocumentId(value: string, name = "id"): string {
  if (value.length === 0 || value.length > 200 || value.includes("/") || value === "." || value === "..") {
    throw badRequest(`Invalid ${name}`);
  }
  return value;
}
