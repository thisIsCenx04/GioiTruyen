export default function AdminLoading() {
  return (
    <section aria-label="Đang tải nội dung" aria-live="polite" className="adminDashboard adminRouteLoading">
      <div className="adminLoadingHeading" />
      <div className="adminLoadingStats">
        {Array.from({ length: 4 }, (_, index) => <i key={index} />)}
      </div>
      <div className="adminLoadingPanel" />
    </section>
  );
}
