import { Link } from "react-router-dom";

import { AdminLoginForm } from "../components/admin-login-form";

export default function AdminLoginPage() {
  return (
    <main className="adminLogin adminLoginBright">
      <section>
        <p>Giới Truyện Admin</p>
        <h1>Đăng nhập quản trị</h1>
        <span>Phiên đăng nhập được dùng cho xét duyệt và các thao tác thay đổi dữ liệu.</span>
        <AdminLoginForm />
        <Link to="/">Quay lại dashboard</Link>
      </section>
    </main>
  );
}
