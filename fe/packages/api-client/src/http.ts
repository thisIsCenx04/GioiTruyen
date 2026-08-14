/**
 * The transport every client in this package shares.
 *
 * The backend answers failures with RFC 7807 problem documents, so an error is
 * carried as one rather than flattened to a message: callers branch on
 * `error.problem.status` (a 404 becomes a Not Found page, a 402 opens the
 * paywall) and show `detail`, which the API writes in Vietnamese for readers.
 */

/** RFC 7807 problem document, as this API emits it. */
export type ProblemDetail = {
  type?: string;
  title?: string;
  status: number;
  /** Reader-facing sentence; the API writes this one in Vietnamese. */
  detail?: string;
  /**
   * Machine-readable code such as "chapter.paid_needs_price". Callers look it
   * up in a message table, so it is always a string: an absent code becomes ""
   * rather than undefined, which no object can be indexed by.
   */
  code: string;
  instance?: string;
};

export class StoryApiError extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`);
    this.name = "StoryApiError";
    this.problem = problem;
  }
}

export type ClientOptions = {
  /** Defaults to the same-origin API root, which is how the site is served. */
  baseUrl?: string;
  /** Lets a caller attach an Authorization header or a timeout. */
  fetchImplementation?: typeof fetch;
};

export type QueryValue = string | number | boolean | null | undefined;

export type RequestOptions = {
  method?: string;
  query?: Record<string, QueryValue>;
  body?: unknown;
  /** Sent as multipart instead of JSON; used by the publishing upload paths. */
  form?: FormData;
  /**
   * Retry key for a write that must not happen twice. Callers generate one per
   * user action and reuse it across retries, so a donation or a withdrawal
   * cannot be charged again by a doubled request.
   */
  idempotencyKey?: string;
  /** Row version for an optimistic-concurrency write. */
  version?: number | string;
  headers?: Record<string, string>;
};

const DEFAULT_BASE_URL = "/api/v1";

function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined || value === "") continue;
    params.set(key, String(value));
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

/**
 * Turns a failed response into a {@link StoryApiError}.
 *
 * A proxy or gateway can fail the request before it reaches the application,
 * and those replies are HTML rather than a problem document - so the body is
 * parsed defensively and the status is always preserved.
 */
async function toError(response: Response): Promise<StoryApiError> {
  let problem: ProblemDetail = { code: "", status: response.status, title: response.statusText };
  try {
    const text = await response.text();
    if (text) {
      const parsed = JSON.parse(text) as Partial<ProblemDetail>;
      problem = {
        ...problem,
        ...parsed,
        code: parsed.code ?? "",
        status: parsed.status ?? response.status,
      };
    }
  } catch {
    // Body was not a problem document; the status alone still describes it.
  }
  return new StoryApiError(problem);
}

export type Transport = <T>(path: string, options?: RequestOptions) => Promise<T>;

export function createTransport(options: ClientOptions = {}): Transport {
  const root = (options.baseUrl ?? DEFAULT_BASE_URL).replace(/\/+$/u, "");
  const call: typeof fetch = options.fetchImplementation
    ?? ((input, init) => fetch(input, init));

  return async function request<T>(path: string, request: RequestOptions = {}): Promise<T> {
    const headers = new Headers();
    let body: BodyInit | undefined;

    if (request.form) {
      // Content-Type is left unset so the browser adds the multipart boundary.
      body = request.form;
    } else if (request.body !== undefined) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(request.body);
    }
    headers.set("Accept", "application/json");
    if (request.idempotencyKey) {
      headers.set("Idempotency-Key", request.idempotencyKey);
    }
    if (request.version !== undefined) {
      // Weak comparison: the server versions rows, not byte-identical bodies.
      headers.set("If-Match", `W/"${request.version}"`);
    }
    for (const [name, value] of Object.entries(request.headers ?? {})) {
      headers.set(name, value);
    }

    const response = await call(`${root}${path}${buildQuery(request.query)}`, {
      method: request.method ?? "GET",
      headers,
      body,
    });

    if (!response.ok) {
      throw await toError(response);
    }
    // 204 and an empty 200 both mean "done, nothing to read".
    if (response.status === 204) {
      return undefined as T;
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  };
}
