export type LegalTable = Readonly<{
  headers: readonly string[];
  rows: readonly (readonly string[])[];
}>;

export type LegalBlock =
  | Readonly<{ type: "paragraph"; text: string }>
  | Readonly<{ type: "list"; items: readonly string[] }>
  | Readonly<{ type: "table"; table: LegalTable }>;

export type LegalSection = Readonly<{
  title: string;
  blocks: readonly LegalBlock[];
}>;

export type LegalPageContent = Readonly<{
  eyebrow: string;
  title: string;
  description: string;
  sections: readonly LegalSection[];
}>;

export const termsContent: LegalPageContent = {
  description: "Quyền, trách nhiệm và điều khoản sử dụng dành cho độc giả, dịch giả và tác giả trên Giới Truyện.",
  eyebrow: "Điều khoản sử dụng",
  title: "Điều khoản sử dụng",
  sections: [
    {
      blocks: [
        { text: "Bạn có thể đăng nhập vào website bằng gmail hoặc facebook.", type: "paragraph" },
        {
          items: [
            "Đọc truyện vip hoặc miễn phí.",
            "Theo dõi, lưu vào tủ truyện và các tính năng khác.",
            "Làm nhiệm vụ từ website hoặc dịch giả và tác giả để nhận được xu.",
          ],
          type: "list",
        },
        { text: "Khi bình luận và đăng tải, người dùng phải tuân thủ các quy định cộng đồng.", type: "paragraph" },
        {
          items: [
            "Không được chửi tục, xúc phạm, công kích cá nhân.",
            "Không được chèn link quảng cáo, lừa đảo, mã độc.",
            "Không được bình luận gây hiểu lầm, xuyên tạc, đả kích làm sai lệch tới chính trị, tôn giáo hoặc các vấn đề liên quan tới chính trị, tôn giáo.",
          ],
          type: "list",
        },
      ],
      title: "I. Quyền của độc giả",
    },
    {
      blocks: [
        {
          items: [
            "Bạn có thể đăng truyện do chính bạn dịch và edit.",
            "Được chia lợi nhuận từ chính nội dung bạn đăng.",
          ],
          type: "list",
        },
        {
          items: [
            "Không dịch và edit những truyện, tác phẩm xuyên tạc, đả kích, gây hiểu lầm làm sai lệch tới chính trị, tôn giáo hoặc các vấn đề liên quan tới chính trị, tôn giáo.",
            "Không đăng những truyện có yếu tố 18+, khiêu dâm, đồi phong bại tục.",
            "Không được reup truyện đã được dịch và đã đăng từ các website, page, mạng xã hội khác.",
            "Không đăng tải nội dung vi phạm bản quyền.",
          ],
          type: "list",
        },
      ],
      title: "II. Quyền và giới hạn của dịch giả, tác giả",
    },
    {
      blocks: [
        { text: "Tỷ lệ nạp: 100.000 vnđ = 90.000 xu và ngọc.", type: "paragraph" },
        {
          items: [
            "Xu dùng để đọc truyện.",
            "Ngọc dùng để đề cử truyện.",
          ],
          type: "list",
        },
      ],
      title: "III. Nạp tiền",
    },
    {
      blocks: [{ text: "Những điều khoản trên có thể thay đổi theo thời gian.", type: "paragraph" }],
      title: "IV. Điều khoản",
    },
  ],
};

export const publishingRulesContent: LegalPageContent = {
  description: "Quy định về chất lượng nội dung, kiểm duyệt, lợi nhuận và rút tiền khi đăng truyện trên Giới Truyện.",
  eyebrow: "Quy định đăng truyện",
  title: "Quy định đăng truyện",
  sections: [
    {
      blocks: [
        {
          items: [
            "Phải được kiểm tra, chỉnh sửa trước khi đăng.",
            "Quy tắc chính tả và cách dùng dấu câu trong tiếng Việt phải chuẩn.",
            "Phải có văn án hoặc giới thiệu truyện đầy đủ.",
            "Tối thiểu 1 chương phải từ 800 chữ đối với truyện dài.",
            "Tối thiểu 1 chương phải từ 1400 chữ đối với truyện ngắn, zhihu.",
          ],
          type: "list",
        },
      ],
      title: "I. Nội dung đăng truyện",
    },
    {
      blocks: [
        {
          items: [
            "Không đăng nội dung 18+, khiêu dâm, đồi phong bại tục.",
            "Không đăng tải nội dung vi phạm bản quyền.",
            "Không được reup truyện đã được dịch và đã đăng từ các website, page, mạng xã hội khác.",
            "Không được chèn link để lôi kéo thành viên qua website, page, mạng xã hội khác.",
            "Không để số tài khoản nhằm kêu độc giả donate; chỉ được donate qua tính năng của website.",
            "Không được dùng tool hoặc các ứng dụng khác để cày view. Nếu phát hiện sẽ bị phạt từ 100.000 vnđ đến 1.000.000 vnđ hoặc khóa tài khoản nếu vi phạm nhiều lần.",
          ],
          type: "list",
        },
      ],
      title: "II. Nội dung bị cấm",
    },
    {
      blocks: [
        { text: "Người đăng có trách nhiệm hoàn toàn về nội dung mình đăng. Nếu có khiếu nại bản quyền thì website có quyền gỡ bỏ tác phẩm.", type: "paragraph" },
        { text: "Thời gian duyệt truyện: 1 - 5 ngày. Sau khi được duyệt, người đăng vẫn phải tuân thủ quy tắc trên. Nếu vi phạm sẽ bị nhắc nhở hoặc bị phạt tùy tình huống.", type: "paragraph" },
      ],
      title: "III. Trách nhiệm và duyệt truyện",
    },
    {
      blocks: [
        {
          items: [
            "Truyện độc quyền: 90%.",
            "Truyện không độc quyền: 70%.",
            "Đối với truyện set vip sẽ không được bật quảng cáo. Nếu bật quảng cáo thì sẽ không được set vip.",
          ],
          type: "list",
        },
        {
          items: [
            "Truyện ngắn, zhihu tự bật quảng cáo để được thanh toán lợi nhuận; nếu không bật sẽ không có lợi nhuận.",
            "Thanh toán theo số lần độc giả click vào link affiliate, quảng cáo.",
            "Lợi nhuận từ 2đ đến 15đ.",
          ],
          type: "list",
        },
      ],
      title: "IV. Lợi nhuận",
    },
    {
      blocks: [
        {
          items: [
            "Tối thiểu 200.000 vnđ cho 1 lần rút.",
            "Dưới 1.000.000 vnđ: phí 20.000 vnđ cho 1 lần rút.",
            "Trên 1.000.000 vnđ: miễn phí rút.",
          ],
          type: "list",
        },
        { text: "Những quy tắc trên có thể thay đổi theo thời gian.", type: "paragraph" },
      ],
      title: "V. Rút tiền và quy tắc",
    },
  ],
};

export const privacyContent: LegalPageContent = {
  description: "Chính sách thu thập, sử dụng, tiếp cận và bảo vệ thông tin người dùng trên Giới Truyện.",
  eyebrow: "Chính sách bảo mật",
  title: "Chính sách bảo mật",
  sections: [
    {
      blocks: [
        {
          items: [
            "Hỗ trợ người dùng khi có nhu cầu.",
            "Giải đáp thắc mắc.",
            "Thông báo cho bạn khi website update thêm tính năng mới.",
            "Nhận góp ý và xem xét để nâng cấp website.",
            "Thanh toán lợi nhuận.",
            "Để truy cập và sử dụng dịch vụ của website, người dùng có thể phải đăng ký một số thông tin và chúng tôi sẽ không chịu mọi trách nhiệm liên quan đến pháp luật của thông tin khai báo.",
          ],
          type: "list",
        },
      ],
      title: "I. Mục đích thu thập thông tin",
    },
    {
      blocks: [
        {
          items: [
            "Khi cần thiết chúng tôi sẽ gửi thư qua gmail như thông báo về tính năng mới, vi phạm quy định điều khoản của website, thư cảm ơn.",
            "Ngăn chặn các hành vi gian lận, vi phạm trên website.",
            "Thống kê lượt xem, số lần bạn click vào link và những thông tin liên quan kết nối với website.",
            "Thu thập địa chỉ IP, thời gian, địa chỉ và yêu cầu sẽ thay đổi theo thời gian.",
          ],
          type: "list",
        },
      ],
      title: "II. Phạm vi sử dụng thông tin",
    },
    {
      blocks: [
        {
          items: [
            "Chủ sở hữu website.",
            "Người dùng đã cung cấp thông tin.",
            "Các cơ quan chức năng khi pháp luật có yêu cầu.",
          ],
          type: "list",
        },
      ],
      title: "III. Tiếp cận thông tin",
    },
    {
      blocks: [
        {
          items: [
            "Người dùng có thể tự quản lý tài khoản, cập nhật dữ liệu.",
            "Người dùng có thể yêu cầu website chỉnh sửa, thay đổi thông tin nếu như bị lỗi, thiếu hụt chi tiết đã cung cấp.",
            "Người dùng có thể yêu cầu website xóa tài khoản, thông tin bất cứ lúc nào.",
            "Người dùng có thể nhắn tin trực tiếp qua hòm thư: gioitruyen2026@gmail.com.",
          ],
          type: "list",
        },
      ],
      title: "IV. Thông tin cá nhân",
    },
    {
      blocks: [
        { text: "Website sẽ sử dụng các biện pháp như kỹ thuật thông tin, quản lý để tránh trường hợp bị lộ thông tin.", type: "paragraph" },
        { text: "Nếu người dùng phát hiện thông tin cá nhân đã cung cấp trên website gioitruyen.com có dấu hiệu bị lộ, bị dùng vào mục đích khác mục I hoặc có vi phạm pháp luật, vui lòng nhắn qua hòm thư ngay lập tức: gioitruyen2026@gmail.com để website xử lý kịp thời.", type: "paragraph" },
      ],
      title: "V. Bảo mật thông tin và giải quyết khiếu nại",
    },
    {
      blocks: [
        {
          items: [
            "Chính sách sẽ thay đổi theo từng thời điểm để phù hợp quy định pháp luật.",
            "Mọi thay đổi sẽ được đăng ở Chính sách bảo mật.",
            "Nếu bạn không đồng ý những thay đổi mà website công bố thì xin vui lòng không sử dụng website nữa.",
          ],
          type: "list",
        },
      ],
      title: "VI. Thay đổi chính sách",
    },
    // Required by the AdSense programme policies: a site running Google ads has
    // to disclose the third-party cookies those ads set, name Google, and point
    // readers at the opt-out. The policy said nothing about advertising at all,
    // which is the most common reason a site is refused or suspended.
    {
      blocks: [
        {
          text: "Website sử dụng dịch vụ quảng cáo của bên thứ ba, trong đó có Google AdSense, để duy trì chi phí vận hành và giữ nội dung miễn phí cho người đọc.",
          type: "paragraph",
        },
        {
          items: [
            "Google và các đối tác quảng cáo của Google sử dụng cookie để hiển thị quảng cáo dựa trên những lần bạn đã truy cập website này hoặc các website khác.",
            "Cookie quảng cáo giúp Google và đối tác hiển thị quảng cáo phù hợp hơn với bạn, đồng thời giới hạn số lần bạn nhìn thấy cùng một quảng cáo.",
            "Bên thứ ba có thể thu thập địa chỉ IP, loại trình duyệt, thiết bị, trang bạn đang xem và thời điểm truy cập. Website không chia sẻ tên đăng nhập, email hay thông tin ví của bạn cho các đơn vị quảng cáo.",
            "Bạn có thể tắt quảng cáo cá nhân hoá bất cứ lúc nào tại Cài đặt quảng cáo của Google: https://www.google.com/settings/ads",
            "Bạn cũng có thể từ chối cookie của các nhà cung cấp khác tại: https://www.aboutads.info/choices",
            "Bạn có thể chặn hoặc xoá cookie trong phần cài đặt trình duyệt. Website vẫn hoạt động bình thường khi cookie quảng cáo bị chặn.",
          ],
          type: "list",
        },
        {
          text: "Chính sách quyền riêng tư của Google được công bố tại: https://policies.google.com/technologies/ads",
          type: "paragraph",
        },
        {
          text: "Website không đặt quảng cáo xen vào giữa nội dung đã trả phí, và không bắt buộc người đọc phải xem hết quảng cáo mới được đọc tiếp.",
          type: "paragraph",
        },
      ],
      title: "VII. Quảng cáo và cookie của bên thứ ba",
    },
  ],
};

export const missionsContent: LegalPageContent = {
  description: "Danh sách nhiệm vụ dành cho dịch giả, tác giả, độc giả tự đăng và nhiệm vụ hàng ngày nhận xu.",
  eyebrow: "Nhiệm vụ",
  title: "Nhiệm vụ",
  sections: [
    {
      blocks: [
        {
          table: {
            headers: ["STT", "Nhiệm vụ", "Truyện", "Thời gian", "Xu", "Tình trạng"],
            rows: [
              ["1", "PR truyện (video tiktok)", "", "", "", "Chưa nhận"],
              ["2", "PR truyện (video/bài page)", "", "", "", "Đang làm"],
              ["3", "Dịch truyện", "", "", "", "Hoàn thành"],
            ],
          },
          type: "table",
        },
      ],
      title: "Dịch giả, tác giả, độc giả tự đăng",
    },
  ],
};

export const affiliateLinksContent: LegalPageContent = {
  description: "Quy định gắn link affiliate, quảng cáo và cách tính lợi nhuận theo lượt click.",
  eyebrow: "Gắn link",
  title: "Gắn link",
  sections: [
    {
      blocks: [
        { text: "Người đăng truyện tự gắn link affiliate, quảng cáo và tính số lần khách hàng click vào link để thanh toán lợi nhuận cho người đăng.", type: "paragraph" },
        { text: "Giá từ 1đ đến 15đ cho 1 lần click.", type: "paragraph" },
      ],
      title: "Quy định gắn link",
    },
  ],
};
