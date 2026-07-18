package com.forexbot.service;

import com.forexbot.dto.WeeklyStatsDto;
import com.forexbot.model.User;
import com.forexbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends the weekly review email to every active user every Friday at 17:05
 * New York time — five minutes after the forex market closes for the weekend.
 *
 * The report covers signals and trades since Sunday 17:00 ET (the week open).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyEmailScheduler {

    private final WeeklyReviewService weeklyReview;
    private final EmailService         emailService;
    private final UserRepository       userRepository;

    @Scheduled(cron = "0 5 17 * * FRI", zone = "America/New_York")
    public void sendWeeklyReviews() {
        log.info("Weekly review email job starting");

        WeeklyStatsDto stats = weeklyReview.thisWeek();
        List<User> users     = userRepository.findAll();

        int sent = 0;
        for (User user : users) {
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                emailService.sendWeeklyReview(user.getEmail(), stats);
                sent++;
            }
        }

        log.info("Weekly review sent to {} users — signals: {}, trades: {}, P&L: {}",
                sent, stats.getTotalSignals(), stats.getTotalTrades(), stats.getTotalPnl());
    }
}
