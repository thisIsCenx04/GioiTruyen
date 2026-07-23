declare module "*.mjs" {
  export function inspectTestSource(
    filePath: string,
    source: string,
  ): ReadonlyArray<Readonly<{ code: string; filePath: string }>>;

  export function mergeJUnitSuites(reports: readonly string[]): string;
}
