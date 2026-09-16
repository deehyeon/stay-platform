package com.stayplatform.stay.adapter.runner;

import com.stayplatform.stay.application.HotelSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class HotelSyncRunner implements ApplicationRunner {

    private final HotelSyncService hotelSyncService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("앱 기동 시 숙소 목록 동기화 시작");
        hotelSyncService.sync();
        log.info("앱 기동 시 숙소 목록 동기화 완료");
    }
}
