"use client";

import { MessageSquare, Send, UserCheck, Sparkles, Shield } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

export interface CommunityMessage {
  id: string;
  userName: string;
  userAvatarTone: string;
  userRole?: string;
  content: string;
  timestamp: string;
}

const INITIAL_MESSAGES: CommunityMessage[] = [
  {
    id: "1",
    userName: "Vũ Đế",
    userAvatarTone: "indigo",
    userRole: "ADMIN",
    content: "Chào mừng các bạn đến với cộng đồng Giới Truyện! Hãy cùng chia sẻ những bộ truyện hay nhé.",
    timestamp: "10 phút trước",
  },
  {
    id: "2",
    userName: "Linh Kiếm Sơn",
    userAvatarTone: "gold",
    userRole: "TEAM",
    content: "Team mình vừa cập nhật chương mới bộ Tiên Luyện Ma Tôn, mời các đạo hữu vào thưởng thức!",
    timestamp: "5 phút trước",
  },
  {
    id: "3",
    userName: "Tiểu Bảo",
    userAvatarTone: "cyan",
    content: "Truyện cuốn quá team ơi! Mong chờ chương tiếp theo từng giờ.",
    timestamp: "Vừa xong",
  },
];

export function CommunityChat({ compact = false }: Readonly<{ compact?: boolean }>) {
  const [messages, setMessages] = useState<CommunityMessage[]>([]);
  const [input, setInput] = useState("");
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [userName, setUserName] = useState("Độc giả Giới Truyện");

  useEffect(() => {
    // Load existing stored messages
    try {
      const stored = localStorage.getItem("gioitruyen_community_messages");
      if (stored) {
        setMessages(JSON.parse(stored));
      } else {
        setMessages(INITIAL_MESSAGES);
        localStorage.setItem("gioitruyen_community_messages", JSON.stringify(INITIAL_MESSAGES));
      }
    } catch {
      setMessages(INITIAL_MESSAGES);
    }

    // Check login state
    const token = localStorage.getItem("access_token") || localStorage.getItem("gioitruyen_token");
    if (token) {
      setIsLoggedIn(true);
      const savedName = localStorage.getItem("gioitruyen_user_name") || "Đạo Hữu Hào Hoa";
      setUserName(savedName);
    }
  }, []);

  function handleSend(e: FormEvent) {
    e.preventDefault();
    if (!input.trim()) return;

    const newMessage: CommunityMessage = {
      id: Date.now().toString(),
      userName: userName,
      userAvatarTone: ["indigo", "cyan", "gold", "blue"][Math.floor(Math.random() * 4)] || "indigo",
      userRole: isLoggedIn ? "USER" : undefined,
      content: input.trim(),
      timestamp: "Vừa xong",
    };

    const updated = [...messages, newMessage];
    setMessages(updated);
    try {
      localStorage.getItem("access_token");
      localStorage.setItem("gioitruyen_community_messages", JSON.stringify(updated.slice(-50)));
    } catch {
      // ignore
    }

    // Persist to backend if API available
    fetch("/api/v1/community/messages", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(newMessage),
    }).catch(() => {
      // Ignore network errors
    });

    setInput("");
  }

  return (
    <section className={`communityChatWidget ${compact ? "compactChat" : "fullChat"}`}>
      <header className="chatHeader">
        <div>
          <MessageSquare aria-hidden="true" className="chatIcon" />
          <h3>Cộng đồng Giới Truyện</h3>
        </div>
        <span className="liveBadge">
          <span className="liveDot" />
          Trực tiếp
        </span>
      </header>

      <div className="chatMessageList">
        {messages.map((msg) => (
          <div key={msg.id} className="chatMessageItem">
            <div className={`chatAvatar avatarTone-${msg.userAvatarTone}`}>
              {msg.userName.slice(0, 1).toUpperCase()}
            </div>
            <div className="chatMessageBody">
              <div className="chatAuthorRow">
                <strong className="chatAuthorName">{msg.userName}</strong>
                {msg.userRole === "ADMIN" && <span className="roleBadge adminRole">Admin</span>}
                {msg.userRole === "TEAM" && <span className="roleBadge teamRole">Team</span>}
                <span className="chatTime">{msg.timestamp}</span>
              </div>
              <p className="chatText">{msg.content}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="chatFooter">
        {isLoggedIn ? (
          <form onSubmit={handleSend} className="chatForm">
            <input
              type="text"
              className="chatInput"
              placeholder="Nhập tin nhắn thảo luận..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
            />
            <button type="submit" className="chatSendBtn" aria-label="Gửi tin nhắn">
              <Send aria-hidden="true" />
            </button>
          </form>
        ) : (
          <div className="chatLoginNotice">
            <span>Đăng nhập để tham gia bình luận trực tiếp</span>
            <Link to="/login" className="chatLoginBtn">
              Đăng nhập ngay
            </Link>
          </div>
        )}
      </div>
    </section>
  );
}
