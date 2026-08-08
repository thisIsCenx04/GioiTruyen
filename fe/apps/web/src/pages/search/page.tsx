
import { CatalogSearch } from "@/components/catalog-search";
import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { catalog } from "@/lib/catalog";

/* metadata removed */

type SearchPageProps = Readonly<{
  searchParams: Promise<{ q?: string }>;
}>;

export default async function SearchPage({
  searchParams,
}: SearchPageProps) {
  const query = (await searchParams).q?.trim() ?? "";
  const results =
    query.length >= 2
      ? await catalog.search(query).catch(() => null)
      : null;

  return (
    <PublicShell>
      <section className="searchPage">
        <header className="pageIntro">
          <p>Mục lục toàn thư viện</p>
          <h1>Tìm một thế giới để bước vào.</h1>
          <CatalogSearch initialQuery={query} />
        </header>

        {query.length < 2 ? (
          <div className="searchPrompt">
            <strong>Bắt đầu với ít nhất hai ký tự.</strong>
            <span>
              Tìm theo tên truyện; bộ lọc thể loại sẽ xuất hiện cùng kết
              quả.
            </span>
          </div>
        ) : results === null ? (
          <div className="searchPrompt" role="status">
            <strong>Chưa thể kết nối thư viện.</strong>
            <span>Vui lòng đợi một chút rồi thử tìm lại.</span>
          </div>
        ) : results.items.length === 0 ? (
          <div className="searchPrompt" role="status">
            <strong>Không tìm thấy “{query}”.</strong>
            <span>Thử một phần tên truyện hoặc cách viết khác.</span>
          </div>
        ) : (
          <section aria-labelledby="results-title">
            <div className="resultHeading">
              <h2 id="results-title">
                {results.items.length} kết quả cho “{query}”
              </h2>
              <span>Đã tìm thấy trong thư viện</span>
            </div>
            <div className="catalogGrid">
              {results.items.map((hit, index) => (
                <CatalogStoryCard
                  index={index}
                  key={hit.story.id}
                  story={hit.story}
                />
              ))}
            </div>
          </section>
        )}
      </section>
    </PublicShell>
  );
}
