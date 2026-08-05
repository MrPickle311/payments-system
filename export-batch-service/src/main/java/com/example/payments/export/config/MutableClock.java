package com.example.payments.export.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class MutableClock extends Clock {
    private volatile Clock delegate = Clock.systemUTC();

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return delegate.withZone(zone);
    }

    @Override
    public Instant instant() {
        return delegate.instant();
    }

    public void setFixedTime(Instant instant) {
        this.delegate = Clock.fixed(instant, delegate.getZone());
    }

    public void reset() {
        this.delegate = Clock.systemUTC();
    }
}
