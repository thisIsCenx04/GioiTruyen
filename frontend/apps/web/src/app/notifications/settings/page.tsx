import type { Metadata } from "next";

import { NotificationSettings } from "../../../components/notification-settings";

export const metadata: Metadata = { title: "Cài đặt thông báo" };

export default function NotificationSettingsPage() {
  return <NotificationSettings />;
}
