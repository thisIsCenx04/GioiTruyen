package com.storyplatform.monetization.application;

import java.nio.charset.StandardCharsets;

/**
 * Builds a VietQR payload (EMVCo merchant-presented QR, Napas 247 flavour).
 *
 * <p>The payload is a sequence of {@code IDLLVALUE} triplets: a two digit id, a
 * two digit length, then the value. Nesting works the same way, so a template
 * holds its own triplets as its value. A CRC-16/CCITT-FALSE over everything up
 * to and including "6304" closes the payload.
 *
 * <p>Generating this ourselves means a reader gets a QR carrying the exact
 * amount and transfer note, so an admin never has to match a payment by hand.
 */
public final class VietQrCodec {

    /** Napas' identifier inside the merchant account template. */
    private static final String NAPAS_GUID = "A000000727";

    private VietQrCodec() {
    }

    /**
     * @param bankBin   6-digit Napas bank id, e.g. "970422" for MB
     * @param account   receiving account number
     * @param amountVnd amount in dong; omitted from the QR when zero
     * @param note      transfer description the payer must keep
     */
    public static String payload(String bankBin, String account, long amountVnd, String note) {
        // Beneficiary: bank id + account number, wrapped in Napas' template.
        String beneficiary = field("00", bankBin) + field("01", account);
        String merchantAccount = field("00", NAPAS_GUID)
                + field("01", beneficiary)
                + field("02", "QRIBFTTA"); // transfer to account

        StringBuilder payload = new StringBuilder()
                .append(field("00", "01"))          // format version
                .append(field("01", "12"))          // 12 = dynamic (single use)
                .append(field("38", merchantAccount))
                .append(field("53", "704"))         // ISO 4217 currency: VND
                .append(amountVnd > 0 ? field("54", String.valueOf(amountVnd)) : "")
                .append(field("58", "VN"));

        if (note != null && !note.isBlank()) {
            // Additional data template; "08" is the purpose-of-transaction field.
            payload.append(field("62", field("08", sanitiseNote(note))));
        }

        payload.append("6304");
        return payload + crc16(payload.toString());
    }

    /** Convenience: the image URL VietQR serves for the same parameters. */
    public static String imageUrl(String bankBin, String account, long amountVnd, String note, String accountName) {
        return "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s"
                .formatted(
                        bankBin,
                        account,
                        amountVnd,
                        urlEncode(sanitiseNote(note)),
                        urlEncode(accountName == null ? "" : accountName));
    }

    private static String field(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    /**
     * Banks reject notes carrying diacritics or punctuation, so the note is
     * reduced to the characters that always survive a transfer.
     */
    private static String sanitiseNote(String note) {
        String ascii = java.text.Normalizer.normalize(note, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return ascii.replaceAll("[^A-Za-z0-9 ]", "").trim();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** CRC-16/CCITT-FALSE: poly 0x1021, init 0xFFFF, no reflection. */
    private static String crc16(String value) {
        int crc = 0xFFFF;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
