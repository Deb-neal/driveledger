# 공식 Pleos STT release 설치 검증 — 2026-11-14

상태: **서명 release 신규 설치 및 실제 release 대상 자동 검사 9개 통과. GitHub 공개 등록·최종 접수는 미완료.**

## 검증한 APK

- 파일: `app/build/outputs/apk/release/app-release.apk`
- 패키지: `com.iancompany.voiceledger`, 버전 `0.4.0-pleos-stt`, versionCode 2.
- minSdk28 / targetSdk34. debuggable 아님.
- 크기: 153,686,100바이트. 일반 저장소 파일 업로드 한도를 넘으므로 GitHub Releases 등록 대상.
- SHA-256: `3d6f0d7f9205b619f6a02402e4474d09f47f109d6020d148cc8c1caa8a11dfe4`.
- release 인증서 SHA-256: `2dfa2efb5183345d708cbb7e43ec9badd6e87a81d684f971dfe2019de805798e`.
- apksigner 서명 검증 성공. 현재 API 키·relay token·relay endpoint·서명 비밀번호가 APK 압축 내부에 없는지 검사했다. 이는 모든 가능한 비밀정보에 대한 포괄적 보증은 아니다.

## 환경과 방법

기존 사용자 AVD와 분리된 `DriveLedger_Release_Verify` / emulator-5556 / user10. Pleos Connect 2.1.0, API34, ARM64, 전체 화면 2560×1440.

검증용 AVD의 기존 테스트 설치만 제거한 뒤 release를 신규 설치했다. 기기에서 설치된 base.apk를 다시 가져와 제출 후보와 SHA-256 일치를 확인했다. 마이크 권한은 테스트 설치 시 부여했으며, 호스트 마이크를 사용하지 않고 합성 PCM을 넣었다. adb reverse 연결은 없다. 실제 제품 APK는 재서명하거나 변경하지 않았다. 별도 instrumentation 테스트 APK만 동일 release 인증서로 서명해 설치했다.

## 통과한 검사

1. 공식 ON_DEVICE STT 합성 발화 → 주차 3,000원 확인 화면, 자동 저장 안 함.
2. 금액 검증·초안 복원·중복 저장 방지·수정·입력 취소·삭제 취소/확인·DB 재조회.
3. 제품 Pleos STT 결과 확인 후 명시적 저장으로 한 건만 추가.
4. 변환 중 취소 이후 늦게 도착한 결과 무시.
5. 화면 이탈 시 녹음 취소 및 기존 초안 유지.
6. 마이크 권한 거부 시 녹음기 생성 안 함.
7. 차량 상태를 잃으면 녹음 취소 및 저장 차단.
8. 무음 입력 정리, 변환·저장 안 함.
9. 오디오 포커스 상실 시 녹음 중지 및 초안 유지.

각 검사를 별도 instrumentation 실행으로 분리하여 9/9 통과했다. 실제 SDK 경로를 쓰는 검사와 가짜 입력/디코더로 제어 조건을 검증하는 검사를 포함한다.

## 초기 실패와 조치

첫 묶음 실행은 과거 Whisper 엔진을 강제로 주입하던 검사에서 대기시간 초과 2건이 발생했다. `VoiceInputTest`의 옛 엔진 주입을 제거하여 제품 기본 Pleos 디코더를 쓰도록 수정했다. 다음 묶음 실행은 합성 저장 검사 도중 테스트 Activity가 PAUSED/STOPPED로 전환되어 정상적으로 음성이 취소됐고, 뒤이어 ActivityScenario.recreate도 실패했다. 해당 실패 로그를 보존한다.

테스트 패키지와 앱을 항목마다 강제 종료하고 새 instrumentation 프로세스에서 실행한 결과 9개 모두 통과했다. 제품 APK의 코드는 수정하지 않았으며 해시도 동일하다. 묶음 실행의 ActivityScenario 전환 간섭은 남은 테스트 도구 제약으로 기록한다.

## 근거와 범위

Git 제외 `artifacts/submission/pleos-release-verification/`의 metadata.json, signature.txt, package.txt, install.txt, installed-release.apk, source-sha256.json, isolated-results.json 및 각 메서드 로그. 첫/두 번째 묶음 실패 로그는 instrumentation.log / instrumentation-final.log에 보존했다. cold-start.txt는 테스트 종료 후 앱 프로세스 재실행 결과다.

사용자 실제 발화 2건은 같은 제품 코드의 debug 설치에서 확인한 별도 근거다. 이번 release 자동 검사는 합성 음성이며 실제 차량·소음 환경의 정확도 검증은 아니다. 사용자가 쓰는 emulator-5554와 기존 기록은 이번 검증에서 수정하지 않았다.
