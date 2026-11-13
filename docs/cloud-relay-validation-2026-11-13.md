# HTTPS 음성 중계 준비 검증 — 2026-11-13

상태: Render 배포·HTTPS 인증/합성 인식 확인 및 서명 release 설치·온라인 UI/기록 흐름 검사 완료. 실제 마이크 재시험·APK 공개 게시·공모전 접수는 별도 미완료.

- 서버 검사 10개 통과: 인증/Origin/형식 거절, WAV/무음, SQLite 원자적 예약, 총량/일/분 상한, 재시작 보존, 동시성, DB 오류 시 차단, 상류 실패/비밀값 비노출. 가짜 상류 사용.
- 기존 Python 검사 11개 통과. 4개 합성 fixture dry-run 검증, 외부 API 호출 없음.
- Gunicorn 27.0.1 실제 HTTP 실행: health 200, 무인증 401, 인증된 무음 200/빈 결과 확인. 일회용 DB/더미 키로 실행, 외부 API 호출 없음.
- `render.yaml`을 Render 공식 JSON Schema로 검증 통과.
- Android JUnit 26개 통과(HTTPS/루프백/토큰 경계 검사 2개 추가).
- `assembleRelease`, `assembleDebugAndroidTest`, `lintDebug` 성공. Lint 오류 0, 경고 23개 남음.
- 실제 운영 설정 대신 예시 HTTPS 주소/토큰을 임시 설정해 debug/release Kotlin 컴파일 성공. 검사 후 임시 설정 파일 제거. 예시 주소로 만든 앱을 설치/배포하지 않음.
- 기존 debug Mac 경로는 유지. HTTPS 빌드는 서버 경유와 외부 처리 안내를 표시하고 명시적 동의 후 녹음한다. 인식 결과 자동 저장은 하지 않는다.

## 실제 배포 검증

- 사용자가 Render에 환경변수를 직접 등록하고 서비스를 배포했다. 서버 소스는 `bd97a6126d6a28785c28f4e8a8369b465ded39f4`.
- 서버: `https://driveledger-26ba.onrender.com`. 루트 `/`는 의도한 404이며 사용자용 웹 화면은 없다.
- `GET /health`: 200, 서비스 상태 ok. 인증 없는 `POST /transcribe`: 401.
- 인증된 합성 fixture `fuel30k.wav` 1회: 200, “주유 3만 원.”, 약 2.31초. 실제 사람의 인식 정확도 측정이 아니다.
- 해당 서버 주소/relay 토큰을 Git 제외 설정으로 연결한 서명 release APK를 빌드했다. OpenAI 키는 APK에 포함하지 않는다.
- 기존 사용자 AVD와 분리한 Pleos 2.1.0/API34/2560×1440 새 AVD에 실제 release를 설치했다. 기존 사용자 데이터는 수정하지 않았다.
- 테스트 전용 instrumentation APK만 release와 같은 인증서로 재서명하여 release 대상 검사. 제품 APK에 테스트 코드를 넣거나 debuggable을 켜지 않았다.
- `adb reverse` 없는 상태에서 `OnlineVoiceTest` 1개 통과(6.988초): 최초 외부 전송 동의 → 합성 PCM → 실제 Render/OpenAI → 주유 30,000원 확인, 저장 전 DB 불변. 추가 유료 API 호출 1회, 실제 마이크는 열지 않았다.
- `LedgerFlowTest` 1개 통과(2.185초): 금액 오류/수정, 중복 저장 방지, 재생성, 저장/합계, 수정, 입력 취소, 삭제 취소/확정, DB 재개방 확인. 검사 데이터 정리 완료.
- 원격 디스크 용량/마운트·Auto Deploy 설정은 사용자 안내를 통해 설정했으나 대시보드에서 독립적으로 재확인하지 않았다. `/health`와 인식 성공만으로 재배포 후 사용량 보존을 검증했다고 보지 않는다.

## 남은 확인

실제 마이크를 사용하는 새 HTTPS release 재시험, 네트워크 실패 안내의 release 재검증, 원격 재시작 후 사용량 보존, 최종 APK 공개 링크·영상·접수 서류가 남는다. 기존 로컬 회귀 결과와 신규 클라우드 검증을 구분한다.
