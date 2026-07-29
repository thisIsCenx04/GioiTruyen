export function RouteLoading() {
  return (
    <section aria-busy="true" className="routeLoading" role="status">
      <div className="loadingHero">
        <span />
        <h1 />
        <p />
        <p />
      </div>
      <div className="loadingGrid">
        {Array.from({ length: 6 }, (_, index) => (
          <article className="loadingCard" key={index}>
            <span />
            <strong />
            <p />
          </article>
        ))}
      </div>
    </section>
  );
}
