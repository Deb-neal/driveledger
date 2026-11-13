# HTTPS 음성 중계 준비 검증 — 2026-11-13

상태: 코드와 로컬 검사 완료. Render 서버 생성, 실제 HTTPS 연결, 최종 APK 설치는 미완료.

- 서버 검사 10개 통과: 인증/Origin/형식 거절, WAV/무음, SQLite 원자적 예약, 총량/일/분 상한, 재시작 보존, 동시성, DB 오류 시 차단, 상류 실패/비밀값 비노출. 가짜 상류 사용.
- 기존 Python 검사 11개 통과. 4개 합성 fixture dry-run 검증, 외부 API 호출 없음.
- Gunicorn 27.0.1 실제 HTTP 실행: health 200, 무인증 401, 인증된 무음 200/빈 결과 확인. 일회용 DB/더미 키로 실행, 외부 API 호출 없음.
- `render.yaml`을 Render 공식 JSON Schema로 검증 통과.
- Android JUnit 26개 통과(HTTPS/루프백/토큰 경계 검사 2개 추가).
- `assembleRelease`, `assembleDebugAndroidTest`, `lintDebug` 성공. Lint 오류 0, 경고 23개 남음.
- 실제 운영 설정 대신 예시 HTTPS 주소/토큰을 임시 설정해 debug/release Kotlin 컴파일 성공. 검사 후 임시 설정 파일 제거. 예시 주소로 만든 앱을 설치/배포하지 않음.
- 기존 debug Mac 경로는 유지. HTTPS 빌드는 서버 경유와 외부 처리 안내를 표시하고 명시적 동의 후 녹음한다. 인식 결과 자동 저장은 하지 않는다.

## 배포 전후 남은 확인

유료 서버/1GB 영속 디스크 생성 및 서버 비밀키 등록, 실제 URL 연결, 인증 실패/HTTPS health 확인, Mac relay/adb reverse 없이 실제 release 음성→확인→저장→합계, 네트워크 실패 시 직접 입력, APK 서명/공개 링크 확인이 남았다. 로컬 검사를 실제 클라우드 또는 실차 사용 검증으로 표현하지 않는다.

Render 생성 화면에 공개 저장소, Singapore, build/start 명령, `/health`, `/var/data` 1GB, 사용량 제한 기본값을 준비했다. 유료 생성 버튼은 누르지 않았고 OpenAI 키/relay 토큰도 아직 전송하지 않았다. 화면의 최종 런타임 및 자동 배포 선택은 생성 직전 재확인한다.
