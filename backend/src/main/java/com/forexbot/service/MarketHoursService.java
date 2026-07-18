package com.forexbot.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Determines whether the forex spot market is currently open.
 *
 * Schedule (New York time, ET):
 *   Opens:  Sunday  17:00 ET
 *   Closes: Friday  17:00 ET
 *   Closed: all day Saturday
 *
 * This is approximate — individual broker sessions and public holidays
 * are not accounted for, but this covers 99 % of normal weekly operation.
 */
@Service
public class MarketHoursService {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    /** True when the market is currently accepting trades. */
    public boolean isOpen() {
        ZonedDateTime now = ZonedDateTime.now(NY);
        DayOfWeek day    = now.getDayOfWeek();
        int hour         = now.getHour();

        return switch (day) {
            case SATURDAY -> false;                    // always closed
            case SUNDAY   -> hour >= 17;               // opens at 17:00 ET
            case FRIDAY   -> hour < 17;               // closes at 17:00 ET
            default       -> true;                     // Mon – Thu: always open
        };
    }

    public boolean isClosed() {
        return !isOpen();
    }

    /**
     * Returns the UTC instant when the current (or most recently completed)
     * trading week began — i.e. the most recent Sunday 17:00 ET.
     * Used to scope "this week" queries.
     */
    public Instant currentWeekStart() {
        ZonedDateTime now = ZonedDateTime.now(NY);

        // Roll back to the most recent Sunday
        ZonedDateTime sunday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                                  .withHour(17).withMinute(0).withSecond(0).withNano(0);

        // If we are that Sunday but before 17:00, go one week further back
        if (!now.isAfter(sunday)) {
            sunday = sunday.minusWeeks(1);
        }

        return sunday.toInstant();
    }
}
