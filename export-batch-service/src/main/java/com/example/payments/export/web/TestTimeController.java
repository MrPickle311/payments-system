package com.example.payments.export.web;

import com.example.payments.export.config.MutableClock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/time")
@RequiredArgsConstructor
public class TestTimeController {

    private final MutableClock mutableClock;

    @PostMapping("/set")
    public String setTime(@RequestParam("instant") String instantStr) {
        Instant instant = Instant.parse(instantStr);
        mutableClock.setFixedTime(instant);
        log.warn("System clock overridden to: {}", instant);
        return "Time set to " + instant;
    }

    @PostMapping("/reset")
    public String resetTime() {
        mutableClock.reset();
        log.warn("System clock reset to system time");
        return "Time reset to system default";
    }
}
