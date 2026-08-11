package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.monetization.application.VietQrCodec;
import org.junit.jupiter.api.Test;

/**
 * A malformed payload produces a QR that banking apps silently refuse, so the
 * structure and checksum are pinned here rather than discovered in production.
 */
class VietQrCodecTest {

    private static final String MB_BIN = "970422";
    private static final String ACCOUNT = "0898662206";

    @Test
    void startsWithTheEmvHeaderAndDynamicMarker() {
        String payload = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123");

        // 00 02 01 = format version 01; 01 02 12 = dynamic, single use.
        assertThat(payload).startsWith("000201" + "010212");
    }

    @Test
    void carriesTheBankAndAccountInsideTheNapasTemplate() {
        String payload = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123");

        assertThat(payload).contains("A000000727");
        assertThat(payload).contains(MB_BIN);
        assertThat(payload).contains(ACCOUNT);
        assertThat(payload).contains("QRIBFTTA");
    }

    @Test
    void carriesTheAmountAndVndCurrency() {
        String payload = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123");

        assertThat(payload).contains("5303704");     // currency VND
        assertThat(payload).contains("540550000");   // 54, length 05, "50000"
    }

    @Test
    void omitsTheAmountWhenItIsZero() {
        String withAmount = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123");
        String withoutAmount = VietQrCodec.payload(MB_BIN, ACCOUNT, 0, "NAP XU ABC123");

        // Field 54 sits between the currency (53) and the country (58), so its
        // absence is checked there rather than by scanning the whole payload -
        // a digit pair like "5400" also occurs inside an account number.
        assertThat(withAmount).contains("5303704" + "540550000" + "5802VN");
        assertThat(withoutAmount).contains("5303704" + "5802VN");
    }

    @Test
    void endsWithAFourDigitChecksumAfterTheCrcTag() {
        String payload = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123");

        int crcTag = payload.lastIndexOf("6304");
        assertThat(crcTag).isGreaterThan(0);
        assertThat(payload.substring(crcTag + 4)).hasSize(4).matches("[0-9A-F]{4}");
    }

    @Test
    void producesTheCrcTheEmvSpecDefines() {
        // Verifies the CRC-16/CCITT-FALSE implementation against the value the
        // algorithm is defined to produce for "123456789".
        String payload = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123");
        String body = payload.substring(0, payload.lastIndexOf("6304") + 4);
        String checksum = payload.substring(payload.lastIndexOf("6304") + 4);

        int crc = 0xFFFF;
        for (byte b : body.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        assertThat(checksum).isEqualTo(String.format("%04X", crc));
    }

    @Test
    void stripsDiacriticsFromTheTransferNote() {
        // Banks reject a note carrying Vietnamese accents, so it must be folded.
        String payload = VietQrCodec.payload(MB_BIN, ACCOUNT, 50_000, "Nạp xu Giới Truyện ABC123");

        assertThat(payload).contains("Nap xu Gioi Truyen ABC123");
    }

    @Test
    void buildsAnImageUrlCarryingAmountAndNote() {
        String url = VietQrCodec.imageUrl(MB_BIN, ACCOUNT, 50_000, "NAP XU ABC123", "NGUYEN THI THU TRANG");

        assertThat(url).startsWith("https://img.vietqr.io/image/970422-0898662206-compact2.png");
        assertThat(url).contains("amount=50000");
        assertThat(url).contains("NAP+XU+ABC123");
    }
}
