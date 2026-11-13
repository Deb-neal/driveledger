# Release 서명 준비 확인 — 2026-11-13

상태: **HTTPS 연결 release의 설치·합성 온라인 UI·기록 흐름 검증 완료. 공개 게시·실제 마이크 재시험·제출은 미완료.**

## 최신 HTTPS 후보

- APK: `app/build/outputs/apk/release/app-release.apk`, 138,322,308바이트.
- SHA-256: `b91a93bd9fb7958a14df65b3d7eb6b6fe2a64b6813c38b613497677b86e173eb`.
- 동일한 release 인증서 서명 검증 성공, debuggable 미설정. 현재 OpenAI 키와 서명 비밀번호가 APK 내부에 없음을 확인.
- 버전 `0.3.0-phase3`, versionCode 1, minSdk28/targetSdk34. 운영 HTTPS 주소와 relay 토큰만 연결.
- 별도 Pleos AVD에 설치/실행 성공. release 대상으로 온라인 합성 검사 1개와 기록 흐름 검사 1개 통과. [상세 검증](cloud-relay-validation-2026-11-13.md).

## 최초 오프라인 후보의 과거 기록

아래 해시/크기와 남은 검증은 최초 후보 당시 기록이며 최신 후보는 위 절을 따른다.

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
