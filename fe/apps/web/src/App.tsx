import { BrowserRouter as Router, Routes, Route, Navigate, useParams, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import Layout from './pages/layout';
import { withAsync } from './lib/withAsync';
import { AdminShell } from './pages/dashboard/admin-shell';
import { GlobalAdvertisementManager } from './features/advertisement/global-advertisement-manager';

import PageL2Fib3V0 from './pages/about/page';
import PageL2FjY291bnQ from './pages/account/page';
import PageL2FmZmlsaWF0ZS1saW5rcw from './pages/affiliate-links/page';
import PromotionPage from './pages/bo-cao/page';
import PageL2F1ZGlv from './pages/audio/page';
import PageL2F1ZGlvLzppZE9yU2x1Zw from './pages/audio/[idOrSlug]/page';
import PageL2F1dGgvZm9yZ290LXBhc3N3b3Jk from './pages/auth/forgot-password/page';
import PageL2F1dGgvZ29vZ2xlL2NhbGxiYWNr from './pages/auth/google/callback/page';
import PageL2F1dGgvbWZh from './pages/auth/mfa/page';
import PageL2F1dGgvcmVnaXN0ZXI from './pages/auth/register/page';
import PageL2F1dGgvcmVzZXQtcGFzc3dvcmQ from './pages/auth/reset-password/page';
import PageL2F1dGgvdmVyaWZ5 from './pages/auth/verify/page';
import PageL2NhdGVnb3JpZXM from './pages/categories/page';
import PageL2NhdGVnb3JpZXMvOnNsdWc from './pages/categories/[slug]/page';
import { ScrollToTop } from './components/scroll-to-top';
import PageTagSlug from './pages/tags/[slug]/page';
import PageQuests from './pages/quests/page';
import AdminQuestsPage from './pages/dashboard/quests/page';
import AdminPaymentMethodsPage from './pages/dashboard/payment-methods/page';
import AdminTopupsPage from './pages/dashboard/topups/page';
import AdminPromotionsPage from './pages/dashboard/promotions/page';
import AdminAuthorApplicationsPage from './pages/dashboard/authors/page';
import AuthorApplicationPage from './pages/dang-ky-dang-truyen/page';
import PageL2NvbW11bml0eQ from './pages/community/page';
import PageL2Rhc2hib2FyZA from './pages/dashboard/page';
import PageL2Rhc2hib2FyZC9jb250ZW50L2NhdGVnb3JpZXM from './pages/dashboard/content/categories/page';
import PageL2Rhc2hib2FyZC9jb250ZW50L3N0b3JpZXM from './pages/dashboard/content/stories/page';
import PageL2Rhc2hib2FyZC9jb250ZW50L3RlYW1z from './pages/dashboard/content/teams/page';
import PageL2Rhc2hib2FyZC9jb250ZW50L3VzZXJz from './pages/dashboard/content/users/page';
import PageL2Rhc2hib2FyZC9maW5hbmNl from './pages/dashboard/finance/page';
import AffiliateLinksAdminPage from './pages/dashboard/affiliate-links/page';
import PageL2Rhc2hib2FyZC9sb2dpbg from './pages/dashboard/login/page';
import PageL2xpYnJhcnk from './pages/library/page';
import PageL2xvZ2lu from './pages/login/page';
import PageL21pc3Npb25z from './pages/missions/page';
import PageL25vdGlmaWNhdGlvbnM from './pages/notifications/page';
import PageL25vdGlmaWNhdGlvbnMvc2V0dGluZ3M from './pages/notifications/settings/page';
import PageLw from './pages/page';
import PageL3ByaXZhY3ktcG9saWN5 from './pages/privacy-policy/page';
import PageL3B1Ymxpc2hpbmctcnVsZXM from './pages/publishing-rules/page';
import PageL3Jhbmtpbmdz from './pages/rankings/page';
import PageL3JlYWQvOmNoYXB0ZXJJZA from './pages/read/[chapterId]/page';
import PageL3NlYXJjaA from './pages/search/page';
import PageL3N0b3JpZXMvZnVsbA from './pages/stories/full/page';
import PageL3N0b3JpZXMvbmV3 from './pages/stories/new/page';
import PageL3N0b3JpZXMvb3JpZ2luYWw from './pages/stories/original/page';
import PageL3N0b3JpZXM from './pages/stories/page';
import PageL3N0b3JpZXMvOmlkT3JTbHVn from './pages/stories/[idOrSlug]/page';
import PageL3RlYW1z from './pages/teams/page';
import PageL3RlYW1zLzp0ZWFtSWQvYW5hbHl0aWNz from './pages/teams/[teamId]/analytics/page';
import PublisherDashboardPage from './pages/teams/[teamId]/dashboard/page';
import TeamManagePage from './pages/teams/[teamId]/manage/page';
import PageL3RlYW1zLzp0ZWFtSWQ from './pages/teams/[teamId]/page';
import PageL3RlYW1zLzp0ZWFtSWQvc3Rvcmllcw from './pages/teams/[teamId]/stories/page';
import PageL3Rlcm1z from './pages/terms/page';
import PageL3RydXllbi86c3RvcnlTbHVn from './pages/truyen/[storySlug]/page';
import PageL3RydXllbi86c3RvcnlTbHVnLzpjaGFwdGVyU2x1Zw from './pages/truyen/[storySlug]/[chapterSlug]/page';
import PageL3dhbGxldA from './pages/wallet/page';
import PageL3poaWh1 from './pages/zhihu/page';

const AsyncPageL2F1ZGlv = withAsync(PageL2F1ZGlv as any);
function WrappedPageL2F1ZGlv() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2F1ZGlv params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2F1ZGlvLzppZE9yU2x1Zw = withAsync(PageL2F1ZGlvLzppZE9yU2x1Zw as any);
function WrappedPageL2F1ZGlvLzppZE9yU2x1Zw() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2F1ZGlvLzppZE9yU2x1Zw params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2F1dGgvcmVzZXQtcGFzc3dvcmQ = withAsync(PageL2F1dGgvcmVzZXQtcGFzc3dvcmQ as any);
function WrappedPageL2F1dGgvcmVzZXQtcGFzc3dvcmQ() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2F1dGgvcmVzZXQtcGFzc3dvcmQ params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2F1dGgvdmVyaWZ5 = withAsync(PageL2F1dGgvdmVyaWZ5 as any);
function WrappedPageL2F1dGgvdmVyaWZ5() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2F1dGgvdmVyaWZ5 params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2NhdGVnb3JpZXM = withAsync(PageL2NhdGVnb3JpZXM as any);
function WrappedPageL2NhdGVnb3JpZXM() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2NhdGVnb3JpZXM params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2NhdGVnb3JpZXMvOnNsdWc = withAsync(PageL2NhdGVnb3JpZXMvOnNsdWc as any);
function WrappedPageL2NhdGVnb3JpZXMvOnNsdWc() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2NhdGVnb3JpZXMvOnNsdWc params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageTagSlug = withAsync(PageTagSlug as any);
function WrappedPageTagSlug() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageTagSlug params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2Rhc2hib2FyZA = withAsync(PageL2Rhc2hib2FyZA as any);
function WrappedPageL2Rhc2hib2FyZA() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2Rhc2hib2FyZA params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2Rhc2hib2FyZC9jb250ZW50L2NhdGVnb3JpZXM = withAsync(PageL2Rhc2hib2FyZC9jb250ZW50L2NhdGVnb3JpZXM as any);
function WrappedPageL2Rhc2hib2FyZC9jb250ZW50L2NhdGVnb3JpZXM() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2Rhc2hib2FyZC9jb250ZW50L2NhdGVnb3JpZXM params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2Rhc2hib2FyZC9jb250ZW50L3N0b3JpZXM = withAsync(PageL2Rhc2hib2FyZC9jb250ZW50L3N0b3JpZXM as any);
function WrappedPageL2Rhc2hib2FyZC9jb250ZW50L3N0b3JpZXM() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2Rhc2hib2FyZC9jb250ZW50L3N0b3JpZXM params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2Rhc2hib2FyZC9jb250ZW50L3RlYW1z = withAsync(PageL2Rhc2hib2FyZC9jb250ZW50L3RlYW1z as any);
function WrappedPageL2Rhc2hib2FyZC9jb250ZW50L3RlYW1z() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2Rhc2hib2FyZC9jb250ZW50L3RlYW1z params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2Rhc2hib2FyZC9jb250ZW50L3VzZXJz = withAsync(PageL2Rhc2hib2FyZC9jb250ZW50L3VzZXJz as any);
function WrappedPageL2Rhc2hib2FyZC9jb250ZW50L3VzZXJz() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2Rhc2hib2FyZC9jb250ZW50L3VzZXJz params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2Rhc2hib2FyZC9maW5hbmNl = withAsync(PageL2Rhc2hib2FyZC9maW5hbmNl as any);
function WrappedPageL2Rhc2hib2FyZC9maW5hbmNl() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2Rhc2hib2FyZC9maW5hbmNl params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL2xpYnJhcnk = withAsync(PageL2xpYnJhcnk as any);
function WrappedPageL2xpYnJhcnk() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL2xpYnJhcnk params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageLw = withAsync(PageLw as any);
function WrappedPageLw() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageLw params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3Jhbmtpbmdz = withAsync(PageL3Jhbmtpbmdz as any);
function WrappedPageL3Jhbmtpbmdz() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3Jhbmtpbmdz params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3JlYWQvOmNoYXB0ZXJJZA = withAsync(PageL3JlYWQvOmNoYXB0ZXJJZA as any);
function WrappedPageL3JlYWQvOmNoYXB0ZXJJZA() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3JlYWQvOmNoYXB0ZXJJZA params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3NlYXJjaA = withAsync(PageL3NlYXJjaA as any);
function WrappedPageL3NlYXJjaA() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3NlYXJjaA params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3N0b3JpZXMvZnVsbA = withAsync(PageL3N0b3JpZXMvZnVsbA as any);
function WrappedPageL3N0b3JpZXMvZnVsbA() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3N0b3JpZXMvZnVsbA params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3N0b3JpZXMvbmV3 = withAsync(PageL3N0b3JpZXMvbmV3 as any);
function WrappedPageL3N0b3JpZXMvbmV3() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3N0b3JpZXMvbmV3 params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3N0b3JpZXMvb3JpZ2luYWw = withAsync(PageL3N0b3JpZXMvb3JpZ2luYWw as any);
function WrappedPageL3N0b3JpZXMvb3JpZ2luYWw() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3N0b3JpZXMvb3JpZ2luYWw params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3N0b3JpZXM = withAsync(PageL3N0b3JpZXM as any);
function WrappedPageL3N0b3JpZXM() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3N0b3JpZXM params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3N0b3JpZXMvOmlkT3JTbHVn = withAsync(PageL3N0b3JpZXMvOmlkT3JTbHVn as any);
function WrappedPageL3N0b3JpZXMvOmlkT3JTbHVn() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3N0b3JpZXMvOmlkT3JTbHVn params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3RlYW1zLzp0ZWFtSWQvYW5hbHl0aWNz = withAsync(PageL3RlYW1zLzp0ZWFtSWQvYW5hbHl0aWNz as any);
function WrappedPageL3RlYW1zLzp0ZWFtSWQvYW5hbHl0aWNz() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3RlYW1zLzp0ZWFtSWQvYW5hbHl0aWNz params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3RlYW1zLzp0ZWFtSWQ = withAsync(PageL3RlYW1zLzp0ZWFtSWQ as any);
function WrappedPageL3RlYW1zLzp0ZWFtSWQ() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3RlYW1zLzp0ZWFtSWQ params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3RlYW1zLzp0ZWFtSWQvc3Rvcmllcw = withAsync(PageL3RlYW1zLzp0ZWFtSWQvc3Rvcmllcw as any);
function WrappedPageL3RlYW1zLzp0ZWFtSWQvc3Rvcmllcw() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3RlYW1zLzp0ZWFtSWQvc3Rvcmllcw params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3RydXllbi86c3RvcnlTbHVnLzpjaGFwdGVyU2x1Zw = withAsync(PageL3RydXllbi86c3RvcnlTbHVnLzpjaGFwdGVyU2x1Zw as any);
function WrappedPageL3RydXllbi86c3RvcnlTbHVnLzpjaGFwdGVyU2x1Zw() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3RydXllbi86c3RvcnlTbHVnLzpjaGFwdGVyU2x1Zw params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}
const AsyncPageL3poaWh1 = withAsync(PageL3poaWh1 as any);
function WrappedPageL3poaWh1() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3poaWh1 params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}

const AsyncPageL3RydXllbi86c3RvcnlTbHVn_Wrapper = withAsync(PageL3RydXllbi86c3RvcnlTbHVn as any);
function WrappedPageL3RydXllbi86c3RvcnlTbHVn() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <AsyncPageL3RydXllbi86c3RvcnlTbHVn_Wrapper params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}

function DashboardLayout({ children }: { children: ReactNode }) {
  return <AdminShell>{children}</AdminShell>;
}

function App() {
  return (
    <Router>
      <ScrollToTop />
      <GlobalAdvertisementManager />
      <Routes>
        <Route element={<Layout />}>
          <Route path="/about" element={<PageL2Fib3V0 />} />
          <Route path="/account" element={<PageL2FjY291bnQ />} />
          <Route path="/affiliate-links" element={<PageL2FmZmlsaWF0ZS1saW5rcw />} />
          <Route path="/bo-cao" element={<PromotionPage />} />
          <Route path="/audio" element={<WrappedPageL2F1ZGlv />} />
          <Route path="/audio/:idOrSlug" element={<WrappedPageL2F1ZGlvLzppZE9yU2x1Zw />} />
          <Route path="/auth/forgot-password" element={<PageL2F1dGgvZm9yZ290LXBhc3N3b3Jk />} />
          <Route path="/auth/google/callback" element={<PageL2F1dGgvZ29vZ2xlL2NhbGxiYWNr />} />
          {/* Sign-in and sign-up live at the bare paths; the /auth/* forms are
              kept as redirects so older links and bookmarks still land. */}
          <Route path="/auth/login" element={<Navigate replace to="/login" />} />
          <Route path="/auth/mfa" element={<PageL2F1dGgvbWZh />} />
          <Route path="/auth/register" element={<Navigate replace to="/register" />} />
          <Route path="/register" element={<PageL2F1dGgvcmVnaXN0ZXI />} />
          <Route path="/auth/reset-password" element={<WrappedPageL2F1dGgvcmVzZXQtcGFzc3dvcmQ />} />
          <Route path="/auth/verify" element={<WrappedPageL2F1dGgvdmVyaWZ5 />} />
          <Route path="/categories" element={<WrappedPageL2NhdGVnb3JpZXM />} />
          <Route path="/categories/:slug" element={<WrappedPageL2NhdGVnb3JpZXMvOnNsdWc />} />
          <Route path="/tags/:slug" element={<WrappedPageTagSlug />} />
          <Route path="/quests" element={<PageQuests />} />
          <Route path="/community" element={<PageL2NvbW11bml0eQ />} />
          <Route path="/dashboard" element={<DashboardLayout><WrappedPageL2Rhc2hib2FyZA /></DashboardLayout>} />
          <Route path="/dashboard/content/categories" element={<DashboardLayout><WrappedPageL2Rhc2hib2FyZC9jb250ZW50L2NhdGVnb3JpZXM /></DashboardLayout>} />
          <Route path="/dashboard/content/stories" element={<DashboardLayout><WrappedPageL2Rhc2hib2FyZC9jb250ZW50L3N0b3JpZXM /></DashboardLayout>} />
          <Route path="/dashboard/content/teams" element={<DashboardLayout><WrappedPageL2Rhc2hib2FyZC9jb250ZW50L3RlYW1z /></DashboardLayout>} />
          <Route path="/dashboard/content/users" element={<DashboardLayout><WrappedPageL2Rhc2hib2FyZC9jb250ZW50L3VzZXJz /></DashboardLayout>} />
          <Route path="/dashboard/finance" element={<DashboardLayout><WrappedPageL2Rhc2hib2FyZC9maW5hbmNl /></DashboardLayout>} />
          <Route path="/dashboard/affiliate-links" element={<DashboardLayout><AffiliateLinksAdminPage /></DashboardLayout>} />
          <Route path="/dashboard/quests" element={<DashboardLayout><AdminQuestsPage /></DashboardLayout>} />
          <Route path="/dashboard/payment-methods" element={<DashboardLayout><AdminPaymentMethodsPage /></DashboardLayout>} />
          <Route path="/dashboard/topups" element={<DashboardLayout><AdminTopupsPage /></DashboardLayout>} />
          <Route path="/dashboard/promotions" element={<DashboardLayout><AdminPromotionsPage /></DashboardLayout>} />
          <Route path="/dashboard/authors" element={<DashboardLayout><AdminAuthorApplicationsPage /></DashboardLayout>} />
          <Route path="/dang-ky-dang-truyen" element={<AuthorApplicationPage />} />
          <Route path="/dashboard/login" element={<PageL2Rhc2hib2FyZC9sb2dpbg />} />
          <Route path="/library" element={<WrappedPageL2xpYnJhcnk />} />
          <Route path="/login" element={<PageL2xvZ2lu />} />
          <Route path="/missions" element={<PageL21pc3Npb25z />} />
          <Route path="/notifications" element={<PageL25vdGlmaWNhdGlvbnM />} />
          <Route path="/notifications/settings" element={<PageL25vdGlmaWNhdGlvbnMvc2V0dGluZ3M />} />
          <Route path="/" element={<WrappedPageLw />} />
          <Route path="/privacy-policy" element={<PageL3ByaXZhY3ktcG9saWN5 />} />
          <Route path="/publishing-rules" element={<PageL3B1Ymxpc2hpbmctcnVsZXM />} />
          <Route path="/rankings" element={<WrappedPageL3Jhbmtpbmdz />} />
          <Route path="/read/:chapterId" element={<WrappedPageL3JlYWQvOmNoYXB0ZXJJZA />} />
          <Route path="/search" element={<WrappedPageL3NlYXJjaA />} />
          <Route path="/stories/full" element={<WrappedPageL3N0b3JpZXMvZnVsbA />} />
          <Route path="/stories/new" element={<WrappedPageL3N0b3JpZXMvbmV3 />} />
          <Route path="/stories/original" element={<WrappedPageL3N0b3JpZXMvb3JpZ2luYWw />} />
          <Route path="/stories" element={<WrappedPageL3N0b3JpZXM />} />
          <Route path="/stories/:idOrSlug" element={<WrappedPageL3N0b3JpZXMvOmlkT3JTbHVn />} />
          <Route path="/teams" element={<PageL3RlYW1z />} />
          <Route path="/teams/:teamId/dashboard" element={<PublisherDashboardPage />} />
          <Route path="/teams/:teamId/manage" element={<TeamManagePage />} />
          <Route path="/teams/:teamId/analytics" element={<WrappedPageL3RlYW1zLzp0ZWFtSWQvYW5hbHl0aWNz />} />
          <Route path="/teams/:teamId" element={<WrappedPageL3RlYW1zLzp0ZWFtSWQ />} />
          <Route path="/teams/:teamId/stories" element={<WrappedPageL3RlYW1zLzp0ZWFtSWQvc3Rvcmllcw />} />
          <Route path="/terms" element={<PageL3Rlcm1z />} />
          <Route path="/truyen/:storySlug" element={<WrappedPageL3RydXllbi86c3RvcnlTbHVn />} />
          <Route path="/truyen/:storySlug/:chapterSlug" element={<WrappedPageL3RydXllbi86c3RvcnlTbHVnLzpjaGFwdGVyU2x1Zw />} />
          <Route path="/wallet" element={<PageL3dhbGxldA />} />
          <Route path="/zhihu" element={<WrappedPageL3poaWh1 />} />
        </Route>
      </Routes>
    </Router>
  );
}
export default App;
