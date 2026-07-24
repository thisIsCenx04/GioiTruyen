self.addEventListener("push", (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = {};
  }
  const title = typeof payload.title === "string"
    ? payload.title
    : "Giới Truyện";
  const body = typeof payload.body === "string"
    ? payload.body
    : "Bạn có một thông báo mới.";
  const candidateUrl = typeof payload.url === "string"
    ? payload.url
    : payload.data?.url;
  const url = typeof candidateUrl === "string"
    && /^\/(?!\/)/u.test(candidateUrl)
    ? candidateUrl
    : "/notifications";
  event.waitUntil(self.registration.showNotification(title, {
    body,
    data: { url },
    tag: typeof payload.tag === "string" ? payload.tag : undefined,
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = event.notification.data?.url ?? "/notifications";
  event.waitUntil(self.clients.openWindow(url));
});
