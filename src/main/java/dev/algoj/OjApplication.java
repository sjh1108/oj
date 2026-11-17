package dev.algoj;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OjApplication {

    /**
     * 서비스 기준 시간대. 엔티티의 LocalDateTime.now()(제출 시각 등)와 로그가 이 시간대의
     * 벽시계 시각으로 찍힌다. 컨테이너 TZ와 무관하게 고정하려고 JVM 기본값을 직접 바꾼다.
     */
    public static final String SERVICE_TIME_ZONE = "Asia/Seoul";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(SERVICE_TIME_ZONE));
        SpringApplication.run(OjApplication.class, args);
    }

}
