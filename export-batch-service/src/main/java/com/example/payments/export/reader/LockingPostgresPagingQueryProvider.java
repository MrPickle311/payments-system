package com.example.payments.export.reader;

import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;

public class LockingPostgresPagingQueryProvider extends PostgresPagingQueryProvider {

    @Override
    public String generateFirstPageQuery(int pageSize) {
        return super.generateFirstPageQuery(pageSize) + " FOR UPDATE SKIP LOCKED";
    }

    @Override
    public String generateRemainingPagesQuery(int pageSize) {
        return super.generateRemainingPagesQuery(pageSize) + " FOR UPDATE SKIP LOCKED";
    }
}
