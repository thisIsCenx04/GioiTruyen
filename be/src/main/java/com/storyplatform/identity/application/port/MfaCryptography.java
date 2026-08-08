package com.storyplatform.identity.application.port;

import java.time.Instant;
import java.util.List;

public interface MfaCryptography {

    EnrollmentSecret generateSecret();

    boolean verifyTotp(
            String protectedSecret,
            String code,
            Instant at
    );

    List<RecoveryCode> generateRecoveryCodes();

    String hashRecoveryCode(String rawCode);

    record EnrollmentSecret(
            String provisioningSecret,
            String protectedSecret
    ) {
    }

    record RecoveryCode(String rawCode, String hash) {
    }
}
