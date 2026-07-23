import { BrandMark, StatusPill, StoryCard } from "@gioitruyen/ui";

const chapters = [
  { number: "128", title: "Dưới chân thành cũ", time: "7 phút trước" },
  { number: "127", title: "Lời hẹn trên sông", time: "Hôm qua" },
  { number: "126", title: "Người giữ đèn", time: "2 ngày trước" },
];

const stories = [
  {
    author: "Lâm Dạ",
    coverTone: "blue" as const,
    eyebrow: "Huyền huyễn · Đang ra",
    href: "#story-1",
    latestChapter: "Ch. 128",
    title: "Người Chép Sử Cuối Cùng",
  },
  {
    author: "An Vi",
    coverTone: "coral" as const,
    eyebrow: "Kỳ ảo đô thị · Trọn bộ",
    href: "#story-2",
    latestChapter: "Ch. 64",
    title: "Thành Phố Không Ngủ",
  },
  {
    author: "Mộc Miên",
    coverTone: "jade" as const,
    eyebrow: "Trinh thám · Độc quyền",
    href: "#story-3",
    latestChapter: "Ch. 39",
    title: "Bản Thảo Màu Lục",
  },
];

export default function HomePage() {
  return (
    <main>
      <header className="siteHeader">
        <a aria-label="Về trang chủ Giới Truyện" href="#">
          <BrandMark />
        </a>
        <nav aria-label="Điều hướng chính">
          <a href="#featured">Khám phá</a>
          <a href="#reading">Tủ truyện</a>
          <a href="#teams">Nhóm xuất bản</a>
        </nav>
        <a className="quietAction" href="#signin">
          Đăng nhập
        </a>
      </header>

      <section className="hero" aria-labelledby="hero-title">
        <div className="heroCopy">
          <div className="heroEyebrow">
            <StatusPill tone="active">12 chương mới hôm nay</StatusPill>
            <span>Tuyển chọn mùa 07</span>
          </div>
          <h1 id="hero-title">
            Đừng chỉ đọc hết.
            <span>Hãy sống cùng từng chương.</span>
          </h1>
          <p className="heroLead">
            Theo dõi bản thảo từ lúc còn thơm mùi mực, bàn luận cùng tác giả và
            trở lại đúng dòng bạn đã dừng.
          </p>
          <div className="heroActions">
            <a className="primaryAction" href="#featured">
              Mở chương mới nhất
            </a>
            <a className="textAction" href="#editor-note">
              Xem lời người biên tập <span aria-hidden="true">↗</span>
            </a>
          </div>
        </div>

        <aside className="chapterRail" id="reading" aria-label="Chương mới của truyện nổi bật">
          <div className="railTop">
            <p>Đang theo dõi</p>
            <span>78%</span>
          </div>
          <h2>Người Chép Sử Cuối Cùng</h2>
          <ol>
            {chapters.map((chapter, index) => (
              <li data-current={index === 0 || undefined} key={chapter.number}>
                <span className="chapterNumber">{chapter.number}</span>
                <span>
                  <strong>{chapter.title}</strong>
                  <small>{chapter.time}</small>
                </span>
              </li>
            ))}
          </ol>
          <a href="#continue">Đọc tiếp chương 128</a>
        </aside>
      </section>

      <section className="featured" id="featured" aria-labelledby="featured-title">
        <div className="sectionHeading">
          <div>
            <p className="sectionEyebrow">Bàn tuyển chọn</p>
            <h2 id="featured-title">Ba thế giới đáng bước vào tuần này</h2>
          </div>
          <a href="#all-stories">Xem toàn bộ truyện</a>
        </div>
        <div className="storyGrid">
          {stories.map((story) => (
            <StoryCard key={story.title} {...story} />
          ))}
        </div>
      </section>

      <section className="editorNote" id="editor-note">
        <span className="editorStamp" aria-hidden="true">
          BT
        </span>
        <div>
          <p className="sectionEyebrow">Ghi chú bên lề</p>
          <blockquote>
            “Một chương hay không kết thúc ở dấu chấm cuối. Nó để lại một cánh
            cửa hé mở trong đầu người đọc.”
          </blockquote>
        </div>
        <p className="noteByline">Ban biên tập · 23.07.2026</p>
      </section>

      <footer>
        <BrandMark />
        <p>Đọc có nhịp. Viết có người đồng hành.</p>
        <a href="#publishing">Gửi bản thảo</a>
      </footer>
    </main>
  );
}
