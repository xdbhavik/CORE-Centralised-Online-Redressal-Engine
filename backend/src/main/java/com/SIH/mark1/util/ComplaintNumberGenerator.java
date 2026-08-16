package com.SIH.mark1.util;

import com.SIH.mark1.repository.ComplaintRepository;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique complaint numbers in the format: GRV-YYYY-NNNNNN
 * e.g. GRV-2026-000001, GRV-2026-000002 ...
 *
 * Counter is seeded from DB on first call so it survives application restarts.
 * Thread-safe via AtomicLong.
 */
@Component
public class ComplaintNumberGenerator {

    private static final String PREFIX = "GRV";

    private final ComplaintRepository complaintRepository;

    /** Lazily initialised counter, -1 means not yet seeded. */
    private final AtomicLong counter = new AtomicLong(-1L);

    /** Track which year the counter was seeded for; reset on year rollover. */
    private volatile int seededYear = -1;

    public ComplaintNumberGenerator(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    /**
     * Returns the next complaint number.
     * Lazily seeds from DB on the first invocation (or on year rollover).
     */
    public synchronized String next() {
        int currentYear = Year.now().getValue();

        if (counter.get() == -1L || seededYear != currentYear) {
            long maxSuffix = complaintRepository.findMaxComplaintSuffixForYear(currentYear);
            counter.set(maxSuffix);
            seededYear = currentYear;
        }

        long sequence = counter.incrementAndGet();
        return String.format("%s-%d-%06d", PREFIX, currentYear, sequence);
    }
}
