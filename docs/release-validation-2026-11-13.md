# Release 서명 준비 확인 — 2026-11-13

상태: **서명 빌드만 확인한 내부 후보. 제출·설치 검증 완료 아님.** 아래 해시/크기는 당일 최초 서명 후보의 기록이며 이후 재빌드된 파일의 식별자가 아니다.

- `:app:assembleRelease` 성공. 별도 RSA 3072 서명키 사용.
- apksigner 검증 성공, v2 서명, 인증서 CN=DriveLedger.
- 인증서 SHA-256: `2dfa2efb5183345d708cbb7e43ec9badd6e87a81d684f971dfe2019de805798e`.
- APK: `app/build/outputs/apk/release/app-release.apk`, 138,321,068바이트.
- APK SHA-256: `3727186220abfc4425cc3e734a4f7fbb885ddf67dc4a0705ce93d0b703502351`.
- 패키지 `com.iancompany.voiceledger`, versionCode 1 / versionName 0.3.0-phase3, minSdk28/targetSdk34.
- `application-debuggable` 없음 확인. APK 내부에 현재 설정된 OpenAI 키/서명 비밀번호 및 키 파일이 없음을 검사.
- `.signing/` 및 `release-signing.properties`는 Git 제외, 파일 권한 600. 서명 비밀번호는 로그/문서에 기록하지 않음.

## 남은 검증

- release의 `LOCAL_ONLINE_VOICE=false`는 유지한다. 이후 HTTPS 온라인 경로(`ONLINE_VOICE`)와 동의 안내 구현 및 예시 주소/토큰을 사용한 debug/release 컴파일 검증을 완료했다. 운영 주소/토큰은 아직 미설정이며 현재 APK를 온라인 제출본으로 배포하지 않는다.
- 배포할 HTTPS 서버와 앱 연결, 동의 안내 갱신, 최종 APK의 설치·음성→확인→저장→합계 검사가 필요하다.
- 이번 확인 시 연결된 adb 기기는 없었다. 설치/실행 검증은 수행하지 않았다.
- 기존 debug 앱은 다른 서명이다. 기존 앱 삭제/데이터 초기화로 설치를 강행하지 않는다. 별도 테스트 환경에서 확인하거나 명시적인 데이터 보존 절차를 먼저 준비한다.
- 독립 서버·시연 영상·최종 서명 서류·접수는 별도 미완료다.

검증 범위: 빌드·서명·패키지 메타데이터·민감 설정 제외. 실제 사용성/실차 검증 결과가 아니다.
