import React, { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught React Error:", error, errorInfo);
  }

  private handleReload = () => {
    window.location.href = "/";
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }
      return (
        <div
          style={{
            minHeight: "100vh",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "2rem",
            background: "#f8fafc",
            fontFamily: "system-ui, -apple-system, sans-serif",
            color: "#0f172a",
          }}
        >
          <div
            style={{
              maxWidth: "28rem",
              width: "100%",
              background: "#ffffff",
              borderRadius: "1rem",
              padding: "2rem",
              boxShadow: "0 1rem 3rem rgba(15, 23, 42, 0.08)",
              textAlign: "center",
              border: "1px solid #e2e8f0",
            }}
          >
            <div
              style={{
                width: "3.5rem",
                height: "3.5rem",
                borderRadius: "50%",
                background: "#fee2e2",
                color: "#dc2626",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "1.5rem",
                fontWeight: "bold",
                marginBottom: "1rem",
              }}
            >
              !
            </div>
            <h2 style={{ margin: "0 0 0.5rem", fontSize: "1.35rem", fontWeight: 800 }}>
              Đã xảy ra sự cố
            </h2>
            <p style={{ color: "#64748b", fontSize: "0.9rem", lineHeight: 1.5, margin: "0 0 1.5rem" }}>
              {this.state.error?.message || "Ứng dụng gặp sự cố ngoài dự kiến khi tải trang này."}
            </p>
            <div style={{ display: "flex", gap: "0.75rem", justifyContent: "center" }}>
              <button
                onClick={() => window.location.reload()}
                style={{
                  padding: "0.65rem 1.25rem",
                  borderRadius: "0.5rem",
                  border: "1px solid #cbd5e1",
                  background: "#ffffff",
                  color: "#334155",
                  fontWeight: 700,
                  cursor: "pointer",
                  fontSize: "0.875rem",
                }}
              >
                Tải lại trang
              </button>
              <button
                onClick={this.handleReload}
                style={{
                  padding: "0.65rem 1.25rem",
                  borderRadius: "0.5rem",
                  border: "none",
                  background: "#0f5fff",
                  color: "#ffffff",
                  fontWeight: 700,
                  cursor: "pointer",
                  fontSize: "0.875rem",
                }}
              >
                Về trang chủ
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
