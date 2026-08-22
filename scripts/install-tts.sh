#!/bin/bash
#
# Cài bộ dựng tiếng cho "Bản đọc tự động" lên máy chủ.
#
# Bản đọc trước đây mượn giọng của máy người nghe (Web Speech API). Nó không tốn
# gì của máy chủ nhưng hỏng ở chỗ nằm ngoài tầm với: máy nào chưa cài giọng
# tiếng Việt thì im lặng, mà phần lớn máy Windows chưa cài. Nay giọng do máy chủ
# dựng, nên mọi người nghe cùng một giọng - kể cả trên iPhone hay một máy
# Windows trắng tinh.
#
# Chạy trên VPS với quyền root:
#   bash install-tts.sh
#
# Kịch bản này chạy lại được nhiều lần: đã có gì thì bỏ qua thứ đó.
set -euo pipefail

TTS_DIR=/opt/tts
CACHE_DIR=/var/lib/gioitruyen-tts
PIPER_VERSION=2023.11.14-2
VOICE_BASE=https://huggingface.co/rhasspy/piper-voices/resolve/main/vi/vi_VN

echo "==> Kiểm tra công cụ"
if ! command -v ffmpeg >/dev/null; then
    echo "    cài ffmpeg (dùng để nén WAV sang Opus)"
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq ffmpeg
fi
ffmpeg -version | head -1

echo "==> Cài piper"
mkdir -p "$TTS_DIR"
cd "$TTS_DIR"
if [ ! -x "$TTS_DIR/piper/piper" ]; then
    curl -sSL -o piper.tar.gz \
        "https://github.com/rhasspy/piper/releases/download/${PIPER_VERSION}/piper_linux_x86_64.tar.gz"
    tar xzf piper.tar.gz
    rm piper.tar.gz
fi
"$TTS_DIR/piper/piper" --version

echo "==> Tải mô hình giọng tiếng Việt"
mkdir -p "$TTS_DIR/voices"
cd "$TTS_DIR/voices"

# Giọng nữ: vais1000 medium, 22 kHz. Chất lượng tốt nhất trong các mô hình
# tiếng Việt công khai của piper.
fetch() {
    local target=$1 url=$2
    if [ ! -s "$target" ]; then
        echo "    tải $target"
        curl -sSL -o "$target" "$url"
    fi
}
fetch vais1000.onnx      "$VOICE_BASE/vais1000/medium/vi_VN-vais1000-medium.onnx"
fetch vais1000.onnx.json "$VOICE_BASE/vais1000/medium/vi_VN-vais1000-medium.onnx.json"

# Giọng nam: VIVOS là bộ nhiều người nói, không có nhãn giới tính. Người nói số
# 50 được chọn bằng cách đo cao độ giọng của mẫu tổng hợp - 131 Hz, nằm gọn
# trong khoảng giọng nam (85-180 Hz), trong khi vais1000 là 245 Hz.
#
# Đây là mô hình x_low 16 kHz nên nghe thô hơn giọng nữ. Chưa có mô hình giọng
# nam tiếng Việt nào chất lượng cao hơn được công bố cho piper.
fetch vivos.onnx      "$VOICE_BASE/vivos/x_low/vi_VN-vivos-x_low.onnx"
fetch vivos.onnx.json "$VOICE_BASE/vivos/x_low/vi_VN-vivos-x_low.onnx.json"

ls -lh "$TTS_DIR"/voices/*.onnx | awk '{print "   ", $9, $5}'

echo "==> Thư mục đệm"
mkdir -p "$CACHE_DIR"
chmod 755 "$CACHE_DIR"
df -h "$CACHE_DIR" | tail -1

echo "==> Thử dựng một câu"
TEXT="Mưa rơi tí tách, con phố vốn nhộn nhịp nay người qua lại thưa thớt."
for voice in "vais1000:" "vivos:--speaker 50"; do
    model=${voice%%:*}
    extra=${voice#*:}
    start=$(date +%s.%N)
    # shellcheck disable=SC2086
    echo "$TEXT" | "$TTS_DIR/piper/piper" --model "$TTS_DIR/voices/$model.onnx" \
        $extra --output_file "/tmp/tts-check-$model.wav" 2>/dev/null
    end=$(date +%s.%N)
    bytes=$(stat -c%s "/tmp/tts-check-$model.wav")
    python3 -c "
took = $end - $start
audio = ($bytes - 44) / 44100
print(f'    $model: {took:.1f}s dựng ra {audio:.1f}s tiếng (hệ số {took/audio:.2f})')
"
    rm -f "/tmp/tts-check-$model.wav"
done

echo
echo "Xong. Backend đọc các đường dẫn này qua app.tts.* trong application.yml;"
echo "mặc định đã trỏ đúng $TTS_DIR và $CACHE_DIR nên không cần đổi gì thêm."
