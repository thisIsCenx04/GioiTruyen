package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.port.TopupQrPayloadFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class VietQrPayloadFactory implements TopupQrPayloadFactory {

    private final String bankBin;
    private final String accountNumber;
    private final String accountName;

    public VietQrPayloadFactory(
            String bankBin,
            String accountNumber,
            String accountName
    ) {
        this.bankBin = digits(bankBin, 6, "bankBin");
        this.accountNumber = digits(accountNumber, 19, "accountNumber");
        this.accountName = safeName(accountName);
    }

    @Override
    public String create(long amountVnd, String transferReference) {
        String beneficiary = field("00", bankBin)
                + field("01", accountNumber);
        String merchantAccount = field("00", "A000000727")
                + field("01", beneficiary)
                + field("02", "QRIBFTTA");
        String additional = field("08", transferReference);
        String withoutCrc = field("00", "01")
                + field("01", "12")
                + field("38", merchantAccount)
                + field("53", "704")
                + field("54", Long.toString(amountVnd))
                + field("58", "VN")
                + field("59", accountName)
                + field("60", "HO CHI MINH")
                + field("62", additional)
                + "6304";
        return withoutCrc + String.format(
                Locale.ROOT,
                "%04X",
                crc16(withoutCrc)
        );
    }

    private static String field(String id, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > 99) {
            throw new IllegalArgumentException("VietQR field is too long.");
        }
        return id + String.format(Locale.ROOT, "%02d", length) + value;
    }

    private static int crc16(String value) {
        int crc = 0xFFFF;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (item & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0
                        ? (crc << 1) ^ 0x1021
                        : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    private static String digits(
            String value,
            int maximum,
            String field
    ) {
        if (value == null
                || value.isBlank()
                || value.length() > maximum
                || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
        return value;
    }

    private static String safeName(String value) {
        if (value == null
                || !value.matches("[A-Z0-9 ]{2,25}")) {
            throw new IllegalArgumentException(
                    "VietQR account name is invalid."
            );
        }
        return value;
    }
}
