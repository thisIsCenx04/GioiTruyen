declare module "*.mjs" {
  export function inspectTestSource(
    filePath: string,
    source: string,
  ): ReadonlyArray<Readonly<{ code: string; filePath: string }>>;

  export function mergeJUnitSuites(reports: readonly string[]): string;

  export const SEEDED_EXIT_CODE: number;
  export const SEEDED_RULE_ID: string;

  export function buildSeededSecret(): string;

  export function assertSeededFindingWasBlocked(
    status: number | null,
    findings: ReadonlyArray<Readonly<{ RuleID?: string }>>,
  ): void;

  export function inspectActionReference(
    reference: string,
  ): string | null;

  export function inspectWorkflowSource(
    filePath: string,
    source: string,
  ): ReadonlyArray<
    Readonly<{
      filePath: string;
      line: number;
      message: string;
      reference: string;
    }>
  >;

  export function findBlockingLicenseFindings(
    report: Readonly<{
      Results?: ReadonlyArray<
        Readonly<{
          Licenses?: ReadonlyArray<
            Readonly<{
              Severity: string;
              PkgName: string;
              Name: string;
            }>
          >;
        }>
      >;
    }>,
  ): ReadonlyArray<
    Readonly<{
      Severity: string;
      PkgName: string;
      Name: string;
    }>
  >;
}
