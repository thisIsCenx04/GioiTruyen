package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.LedgerTransaction;

public interface LedgerBalanceProjector {

    void project(LedgerTransaction transaction);
}
