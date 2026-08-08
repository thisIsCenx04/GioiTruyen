import type { LegalPageContent } from "@/lib/legal-content";

export function LegalContentPage({
  content,
}: Readonly<{ content: LegalPageContent }>) {
  return (
    <article className="informationPage policyPage">
      <header>
        <p>{content.eyebrow}</p>
        <h1>{content.title}</h1>
        <span>{content.description}</span>
      </header>
      {content.sections.map((section) => (
        <section key={section.title}>
          <h2>{section.title}</h2>
          <div className="legalBody">
            {section.blocks.map((block, index) => {
              if (block.type === "paragraph") {
                return <p key={`${section.title}-${index}`}>{block.text}</p>;
              }

              if (block.type === "list") {
                return (
                  <ul key={`${section.title}-${index}`}>
                    {block.items.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                );
              }

              return (
                <div className="legalTableWrap" key={`${section.title}-${index}`}>
                  <table>
                    <thead>
                      <tr>
                        {block.table.headers.map((header) => (
                          <th key={header}>{header}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {block.table.rows.map((row, rowIndex) => (
                        <tr key={`${section.title}-${rowIndex}`}>
                          {row.map((cell, cellIndex) => (
                            <td key={`${section.title}-${rowIndex}-${cellIndex}`}>
                              {cell || "Đang cập nhật"}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              );
            })}
          </div>
        </section>
      ))}
    </article>
  );
}
