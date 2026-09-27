-- V7에서 만든 선정 문제 알림 기록 테이블을 없앤다.
-- 기록은 주차 기본값(그 달 마지막 주차 + 1)을 채우는 용도뿐이라, 관리자 브라우저의
-- localStorage로 옮겼다. 서버는 이제 디스코드로 보내기만 한다.
DROP TABLE IF EXISTS problem_announcements;
