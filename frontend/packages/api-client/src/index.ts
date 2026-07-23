export type ApiProblem = Readonly<{
  type: string;
  title: string;
  status: number;
  code: string;
  detail?: string;
  traceId?: string;
  errors?: Readonly<Record<string, readonly string[]>>;
}>;

export class StoryApiError extends Error {
  readonly problem: ApiProblem;

  constructor(problem: ApiProblem) {
    super(problem.title);
    this.name = "StoryApiError";
    this.problem = problem;
  }
}

export type StoryApiClientOptions = Readonly<{
  baseUrl: string;
  fetchImplementation?: typeof fetch;
}>;

export type RequestOptions = Omit<RequestInit, "body"> & Readonly<{
  body?: unknown;
}>;

export function createStoryApiClient({
  baseUrl,
  fetchImplementation = fetch,
}: StoryApiClientOptions) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");

  async function request<Response>(
    path: `/${string}`,
    options: RequestOptions = {},
  ): Promise<Response> {
    const { body: requestBody, ...requestOptions } = options;
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");

    let body: BodyInit | undefined;
    if (requestBody !== undefined) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(requestBody);
    }

    const response = await fetchImplementation(`${normalizedBaseUrl}${path}`, {
      ...requestOptions,
      ...(body === undefined ? {} : { body }),
      headers,
    });

    if (!response.ok) {
      const problem = (await response.json()) as ApiProblem;
      throw new StoryApiError(problem);
    }

    if (response.status === 204) {
      return undefined as Response;
    }

    return (await response.json()) as Response;
  }

  return Object.freeze({ request });
}
