import { BrandMark, StatusPill } from "@gioitruyen/ui";

const reviewQueue = [
  {
    chapter: "Ch. 128 · Dưới chân thành cũ",
    owner: "Nhóm Lam Sơn",
    state: "Chờ duyệt",
    submitted: "09:42",
    tone: "attention" as const,
  },
  {
    chapter: "Ch. 40 · Mực chưa khô",
    owner: "Mộc Miên",
    state: "Đang soát",
    submitted: "09:18",
    tone: "neutral" as const,
  },
  {
    chapter: "Ch. 65 · Đêm thứ một nghìn",
    owner: "Tổ Sao Khuya",
    state: "Sẵn sàng",
    submitted: "08:55",
    tone: "active" as const,
  },
];

export default function AdminPage() {
  return (
    <main className="adminShell">
      <aside className="sidebar">
        <a
          aria-label="Giới Truyện Admin"
          className="adminBrand desktopBrand"
          href="#"
        >
          <BrandMark inverse />
        </a>
        <a
          aria-label="Giới Truyện Admin"
          className="adminBrand mobileBrand"
          href="#"
        >
          <BrandMark compact inverse />
        </a>
        <nav aria-label="Điều hướng quản trị">
          <p>Vận hành</p>
          <a className="active" href="#overview">
            <span aria-hidden="true">⌁</span> Tổng quan
          </a>
          <a href="#publishing">
            <span aria-hidden="true">¶</span> Xuất bản
          </a>
          <a href="#moderation">
            <span aria-hidden="true">◇</span> Kiểm duyệt
          </a>
          <p>Tài chính</p>
          <a href="#wallets">
            <span aria-hidden="true">◎</span> Ví &amp; giao dịch
          </a>
          <a href="#withdrawals">
            <span aria-hidden="true">↗</span> Rút tiền
          </a>
        </nav>
        <div className="operator">
          <span>HN</span>
          <div>
            <strong>Hà Nguyên</strong>
            <small>Quản trị viên</small>
          </div>
        </div>
      </aside>

      <section className="workspace" id="overview">
        <header className="adminHeader">
          <div>
            <p>Thứ Năm · 23 tháng 7</p>
            <h1>Bàn biên tập</h1>
          </div>
          <div className="headerActions">
            <button type="button">Tìm kiếm <kbd>⌘ K</kbd></button>
            <a href="#new-review">Mở phiên duyệt</a>
          </div>
        </header>

        <section className="signalStrip" aria-label="Tình trạng hệ thống">
          <div>
            <span className="signalMark" />
            <p>
              <strong>Hệ thống ổn định</strong>
              API và hàng đợi xuất bản đang hoạt động bình thường.
            </p>
          </div>
          <a href="#system">Xem sức khỏe hệ thống</a>
        </section>

        <section className="metrics" aria-label="Chỉ số hôm nay">
          <article>
            <p>Chương đã xuất bản</p>
            <strong>38</strong>
            <small>+9 so với hôm qua</small>
          </article>
          <article>
            <p>Đang chờ duyệt</p>
            <strong>12</strong>
            <small>3 mục quá 30 phút</small>
          </article>
          <article>
            <p>Báo cáo cần xử lý</p>
            <strong>07</strong>
            <small>Không có mức khẩn cấp</small>
          </article>
        </section>

        <section className="queue" id="publishing">
          <div className="queueHeading">
            <div>
              <p className="sectionLabel">Luồng trực tiếp</p>
              <h2>Hàng đợi xuất bản</h2>
            </div>
            <a href="#all-reviews">Mở toàn bộ hàng đợi</a>
          </div>
          <div className="queueTable" role="table" aria-label="Hàng đợi xuất bản">
            <div className="tableHeader" role="row">
              <span role="columnheader">Bản thảo</span>
              <span role="columnheader">Người gửi</span>
              <span role="columnheader">Thời gian</span>
              <span role="columnheader">Trạng thái</span>
            </div>
            {reviewQueue.map((item) => (
              <a className="tableRow" href="#review" key={item.chapter} role="row">
                <strong role="cell">{item.chapter}</strong>
                <span role="cell">{item.owner}</span>
                <time role="cell">{item.submitted}</time>
                <span role="cell">
                  <StatusPill tone={item.tone}>{item.state}</StatusPill>
                </span>
              </a>
            ))}
          </div>
        </section>
      </section>
    </main>
  );
}
