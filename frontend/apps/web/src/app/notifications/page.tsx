import type { Metadata } from "next";

import { NotificationInbox } from "../../components/notification-inbox";

export const metadata: Metadata = { title: "Thông báo" };

export default function NotificationsPage() {
  return <NotificationInbox />;
}
