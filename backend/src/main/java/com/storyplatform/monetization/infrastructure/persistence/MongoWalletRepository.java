package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application
        .InsufficientWalletBalanceException;
import com.storyplatform.monetization.application.port.WalletRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoWalletRepository implements WalletRepository {

    private final MongoTemplate mongo;

    public MongoWalletRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<AccountBalance> find(
            WalletAccount.OwnerType ownerType,
            String ownerId
    ) {
        var account = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("ownerType").is(ownerType.name()),
                        Criteria.where("ownerId").is(ownerId),
                        Criteria.where("currency").is("XU")
                )),
                MongoWalletAccountDocument.class
        );
        return Optional.ofNullable(account).map(this::withBalance);
    }

    @Override
    public AccountBalance insert(WalletAccount account) {
        var stored = mongo.insert(MongoWalletAccountDocument.from(account));
        var balance = mongo.insert(new MongoWalletBalanceDocument(
                account.id(),
                0,
                0,
                0,
                account.createdAt()
        ));
        return map(stored, balance);
    }

    @Override
    public void project(
            LedgerTransaction transaction,
            Instant updatedAt
    ) {
        transaction.entries().forEach(entry -> apply(entry, updatedAt));
    }

    private void apply(LedgerEntry entry, Instant updatedAt) {
        var account = mongo.findById(
                entry.accountId(),
                MongoWalletAccountDocument.class
        );
        if (account == null
                || account.toDomain().status()
                != WalletAccount.Status.ACTIVE) {
            throw new IllegalStateException(
                    "Ledger account is missing or inactive."
            );
        }
        boolean increases = account.toDomain().normalSide() == entry.side();
        String field = entry.bucket() == LedgerEntry.Bucket.AVAILABLE
                ? "availableXu"
                : "reservedXu";
        Criteria criteria = Criteria.where("_id").is(entry.accountId());
        if (!increases) {
            criteria = new Criteria().andOperator(
                    criteria,
                    Criteria.where(field).gte(entry.amountXu())
            );
        }
        long delta = increases ? entry.amountXu() : -entry.amountXu();
        var result = mongo.updateFirst(
                Query.query(criteria),
                new Update()
                        .inc(field, delta)
                        .inc("version", 1)
                        .set("updatedAt", updatedAt),
                MongoWalletBalanceDocument.class
        );
        if (result.getModifiedCount() != 1) {
            throw new InsufficientWalletBalanceException();
        }
    }

    private AccountBalance withBalance(
            MongoWalletAccountDocument account
    ) {
        var balance = mongo.findById(
                account.id(),
                MongoWalletBalanceDocument.class
        );
        if (balance == null) {
            throw new IllegalStateException(
                    "Wallet balance projection is missing."
            );
        }
        return map(account, balance);
    }

    private static AccountBalance map(
            MongoWalletAccountDocument account,
            MongoWalletBalanceDocument balance
    ) {
        return new AccountBalance(
                account.toDomain(),
                balance.availableXu(),
                balance.reservedXu(),
                balance.version(),
                balance.updatedAt()
        );
    }
}
