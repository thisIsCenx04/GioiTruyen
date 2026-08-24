import type { OperationOutcome } from "@/components/operation-dialog";

/**
 * Biến một phản hồi lỗi của API thành thông báo người dùng đọc được.
 *
 * <p>Máy chủ trả về RFC 7807: có `code`, `title`, `detail`. Trước đây màn hình
 * chỉ hiện `detail` - thường là một câu tả hiện trạng ("Nhiệm vụ đã hết suất")
 * mà không nói người đọc nên làm gì tiếp. Bảng dưới ghép thêm bước kế tiếp cho
 * từng mã lỗi, nên câu trả lời luôn gồm đủ ba phần: chuyện gì, vì sao, giờ sao.
 *
 * <p>Mã lạ vẫn hiển thị được: rơi về `detail` của máy chủ, và nếu cả cái đó
 * cũng không có thì về mã HTTP - không bao giờ ra hộp thoại trống.
 */

export type ApiProblem = {
  code?: string;
  detail?: string;
  title?: string;
  traceId?: string;
};

/** Bước kế tiếp cho từng mã lỗi. Chỉ nói việc làm được, không nhắc lại lỗi. */
const NEXT_STEP: Record<string, string> = {
  "pr.already_claimed": "Bạn đã nhận nhiệm vụ này rồi. Mở tab “Nhiệm vụ PR của tôi” để gửi kết quả.",
  "pr.already_published": "Nhiệm vụ đã lên bảng nên không sửa được nữa. Muốn đổi thì dừng nhiệm vụ rồi tạo nhiệm vụ mới.",
  "pr.budget_too_high": "Giảm số Xu mỗi suất hoặc giảm số suất, rồi thử lại.",
  "pr.contact_required": "Điền kênh liên hệ để người nhận trao đổi lại được với nhóm.",
  "pr.dispute_closed": "Khiếu nại này đã có kết luận. Xem lại phần kết luận ở dưới thẻ.",
  "pr.dispute_open": "Đang có khiếu nại chờ quản trị viên xử lý, nên chưa thao tác tiếp được.",
  "pr.escrow_empty": "Xu của nhiệm vụ này đã trả hết, không còn gì để hoàn.",
  "pr.forbidden": "Tài khoản của bạn không có quyền với nhiệm vụ này.",
  "pr.insufficient_balance": "Nạp thêm Xu, hoặc giảm ngân sách rồi Publish lại.",
  "pr.no_slot_left": "Suất cuối vừa có người nhận trước. Tải lại bảng để xem nhiệm vụ còn trống.",
  "pr.not_found": "Nhiệm vụ có thể vừa bị gỡ. Tải lại trang.",
  "pr.not_open": "Nhiệm vụ đã đóng hoặc hết hạn đăng ký nên không nhận thêm được.",
  "pr.not_pending": "Đơn này đã được xử lý rồi. Tải lại danh sách để thấy trạng thái mới.",
  "pr.not_submittable": "Chỉ gửi kết quả được khi suất đang ở trạng thái “Đã nhận”.",
  "pr.not_submitted": "Chỉ duyệt được sau khi người nhận đã nộp link bài đăng.",
  "pr.own_team": "Bạn không nhận được nhiệm vụ của chính nhóm mình.",
  "pr.too_many_claims": "Bạn đang giữ quá nhiều nhiệm vụ chưa nộp. Hoàn thành bớt rồi quay lại.",
  "pr.url_required": "Dán link công khai tới bài đăng - nhóm phải mở được link đó để duyệt.",
  "pr.wrong_status": "Trạng thái nhiệm vụ vừa thay đổi. Tải lại trang rồi thao tác lại.",
};

const HTTP_FALLBACK: Record<number, string> = {
  401: "Phiên đăng nhập đã hết hạn. Đăng nhập lại rồi thử lại.",
  403: "Tài khoản của bạn không có quyền thực hiện việc này.",
  404: "Không tìm thấy dữ liệu - có thể vừa bị xoá. Tải lại trang.",
  409: "Dữ liệu vừa thay đổi ở nơi khác. Tải lại trang rồi thao tác lại.",
  429: "Bạn thao tác hơi nhanh. Chờ một chút rồi thử lại.",
  500: "Máy chủ gặp sự cố. Thử lại sau ít phút; nếu vẫn lỗi, báo quản trị viên kèm mã ở dưới.",
  503: "Máy chủ đang bận. Thử lại sau ít phút.",
};

/** Đọc thân lỗi. Một phản hồi không phải JSON cũng không được làm hỏng gì. */
export async function readProblem(response: Response): Promise<ApiProblem> {
  return (await response.json().catch(() => ({}))) as ApiProblem;
}

/**
 * @param title tiêu đề nói việc gì không thành, theo giọng của màn hình gọi nó
 *              ("Không nhận được nhiệm vụ"), chứ không phải giọng của máy chủ.
 */
export function problemOutcome(
  title: string,
  response: Response,
  problem: ApiProblem,
): OperationOutcome {
  const reason = problem.detail?.trim()
    || HTTP_FALLBACK[response.status]
    || `Máy chủ trả về lỗi HTTP ${response.status}.`;
  const details = [reason];

  // traceId là thứ duy nhất nối được màn hình này với dòng log trên máy chủ,
  // nên khi lỗi không tự giải thích được thì phải cho người dùng cầm theo.
  if (response.status >= 500 && problem.traceId) {
    details.push(`Mã sự cố: ${problem.traceId}`);
  }

  return {
    details,
    hint: (problem.code ? NEXT_STEP[problem.code] : undefined) ?? HTTP_FALLBACK[response.status],
    kind: "error",
    title,
  };
}

/** Gộp hai bước trên: dùng ở mọi chỗ chỉ cần “lỗi thì báo cho tử tế”. */
export async function failureOutcome(
  title: string,
  response: Response,
): Promise<OperationOutcome> {
  return problemOutcome(title, response, await readProblem(response));
}
