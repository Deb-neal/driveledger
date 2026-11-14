# 공식 Pleos STT 실행 검증 — 2026-11-14

## 11/14 release 설치 검증 완료 — 최신 상태

공식 STT `0.4.0-pleos-stt` 서명 release를 별도 Pleos AVD에 신규 설치하고, 설치 파일의 SHA-256 일치와 debuggable 아님을 확인했다. 항목별 독립 실행으로 공식 STT·명시적 저장·CRUD·취소·권한·차량 상태·포커스 검사 9개 통과. 이전 묶음 실행의 실패와 테스트 도구 제약은 [검증 기록](release-validation-2026-11-14.md)에 남겼다. 기존 사용자 에뮬레이터와 기록은 변경하지 않았다.

다음은 최종 소스·README와 GitHub Releases APK 공개 및 링크 확인 → 양식 최종화·본인 동의/서명 → 이메일 제출·접수 확인이다. 영상은 제외. 이전 설치 미완료 문구는 당시 기록으로 보존한다.


## 11/14 실제 사용자 발화 확인 — 최신 상태

사용자가 현재 설치된 `0.4.0-pleos-stt-debug`에서 아래 두 문장의 금액·항목이 모두 정확히 표시되었다고 확인했다.

| 발화 | 항목 | 금액 | 근거 |
| --- | --- | ---: | --- |
| 주유비 삼천 원 | 주유 | 3,000원 | 사용자 확인 |
| 주차비 육천 원 | 주차 | 6,000원 | 사용자 확인 |

`삼천워`는 앱 오인식이 아니라 채팅 오타라고 사용자가 정정했다. 공식 Pleos STT ON_DEVICE의 실제 마이크 기본 입력 2건을 통과로 기록한다. 저장 버튼 실행·정밀 지연·추가 금액/소음/실차·release 설치 시험까지 확인한 결과는 아니다. 과거 합성 주유 오인식 결과는 삭제하지 않는다.

다음 단계는 새 서명 release APK 설치 검증 → 최종 소스·README와 APK 공개 → 공식 양식 최종화·본인 동의/서명 → 이메일 제출 및 접수 확인이다. 선택 시연 영상은 제외한다. 이전 문단의 제품 통합·실제 발화 미완료 표시는 당시 기록이며 이 최신 상태가 우선한다.


## 결과

공식 SpeechToText 3.3.1을 사용한 별도 진단 앱에서 ON_DEVICE 서비스 연결·초기화·최종 인식 결과 수신을 확인했다. SDK 파일만 내려받은 상태와 구분한다. 다만 주유를 주요로 오인식하는 사례가 있으므로 정확도와 제품 통합 완료로 해석하지 않는다.

| 합성 입력의 의미 | SDK 최종 반환 원문 | 측정 시간 |
| --- | --- | --- |
| 주유 3만원 | 주요 3만원 | 1.22초 |
| 주차비 3천원 | 오늘 주차비 3000 | 1.2초 |
| 주유 3천원 | 주요 3000원 | 1.18초 |
| 주유 7만원 | 오늘 주요 칠만 | 1.19초 |

시간은 진단 Activity 시작부터 결과 감지까지 1초 간격으로 확인한 값이며 정밀 지연 측정이 아니다. 첫 별도 실행은 초기 모델 준비를 포함해 약 4.7초 후 결과가 왔다. 이후 4회 측정은 준비된 엔진에서 실행했다. 각 합성 파일은 1회씩 비교했으며 실제 사람 발화의 정확도 수치로 사용할 수 없다. 반환 원문을 보정하지 않았다.

## 환경과 절차

- 기존 사용자 AVD와 별개인 `DriveLedger_Release_Verify` / emulator-5556, Pleos Connect 2.1.0, API34, ARM64, 2560×1440, user10.
- 별도 패키지 `com.iancompany.sttprobe`, 공식 Maven `ai.pleos.playground:SpeechToText:3.3.1`과 전이 의존성으로 빌드. STT_SERVICE 권한 granted=true 확인.
- Mode.ON_DEVICE → initialize(OnInitializeListener) → setLocale(Locale.KOREA) → ResultListener → sendAudio(PCM) → completeAudioSend. 네 문장 모두 INIT_SUCCESS 및 final=true 수신.
- 기존 공개 합성 fixture 4개를 16kHz/mono/PCM16으로 공급. 실제 마이크 request(), OpenAI 요청, 계정 키 등록은 하지 않았다.
- 공식 STT 서버 모드(HYBRID/SERVER)와 계정 승인/앱 등록 상태는 시험하지 않았다.
- 진단 앱에서 SDK를 직접 검사했으며 차계부 제품 코드·기존 기록은 변경하지 않았다. 제품 APK는 아직 기존 OpenAI 연결 후보이므로 제출 보류를 유지한다.
- 자동 실행 첫 시도에서 프로세스 생성 직전 pidof 조회가 비어 검사 스크립트가 중단됐다. 프로세스 시작을 기다리도록 진단 스크립트를 수정한 뒤 4회 검사를 마쳤다. SDK 초기화 실패로 계산하지 않는다.

## 근거 파일

Git 제외 로컬 `artifacts/submission/stt-availability/` 아래 results.json, 각 fixture 로그, probe-build.log, run_probe.py. 진단 앱 소스는 `artifacts/submission/stt-probe/`에 있다. 이 진단 APK는 제출본이 아니다.

## 남은 작업

공식 STT를 제품 입력 흐름에 연결하고 기존 취소/권한/주차 상태/오디오 포커스 처리를 유지해야 한다. 실제 목소리에서 주유 오인식과 7천/7만/17만 금액을 확인한다. 항목을 찾지 못할 때 임의 확정하지 않고 직접 수정/선택 후 저장하도록 검증한다. 이후 새 서명 release를 설치·검사하고 소스/README와 일치시켜 공개한다. 기존 Render 서버는 이 검사에서 정지하거나 변경하지 않았다.

## 공식 자료

- [공모전 개발 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)
- [SDK 모드와 초기 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)
- [PCM 입력](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data)

## 제품 연결 및 사용자 테스트 준비 (11/14)

- `0.4.0-pleos-stt` (versionCode 2): 제품 `VoiceInput`의 기본 엔진을 공식 SDK 3.3.1 ON_DEVICE로 교체. 기존 전경 녹음·P 상태 제한·취소·사용자 확인 저장 유지.
- SDK가 `오늘 주차비 3000`처럼 원을 생략하는 결과를 반환하여 첫 제품 검사는 금액 미표시로 실패했다. 음성 결과에 한해 정확한 알려진 항목+단일 금액 형식을 처리하도록 보완했다. 주요→주유 같은 항목 추측은 하지 않는다.
- 재검사: 실제 SDK 합성 PCM→주차 3,000원 초안 표시·미저장 1개, 음성 회귀 7개, CRUD 1개, 총 9개 통과 (82.28초). `artifacts/submission/pleos-integration-tests-final.log`.
- JVM 단위 검사 27개 통과. debug/release 빌드 및 lint 통과 (lint 0 errors, 기존 경고 31개). `artifacts/submission/pleos-final-build.log`.
- debug/release에서 과거 온라인 설정을 활성화할 수 없으며 provider key·relay token·relay endpoint APK 스캔에서 없음 확인. Render 서버를 종료한 것은 아니다.
- 기존 사용자 AVD에 `adb install -r`로 debug 업데이트 성공. 삭제/데이터 초기화 없음. 업데이트 후 기존 기록 2건을 확인하고 DB를 백업했으며 앱 실행 전후 DB 바이트 동일. 설치 전 백업 시도는 사용자 인자 문제로 실패했으므로 설치 전후 DB 해시 비교로 주장하지 않는다.
- 사용자 AVD의 호스트 마이크 연결을 켜고 차계부 MainActivity를 실행했다. 실제 마이크 녹음은 사용자 버튼 입력을 기다린다.
- debug SHA-256: `3a66203a0c0580d1e39ef43a7bf5fb2b94d9f9a75c88521c724e7eb725ce08a0`.
- release SHA-256: `3d6f0d7f9205b619f6a02402e4474d09f47f109d6020d148cc8c1caa8a11dfe4`. 이번 새 release의 별도 설치 검증·실제 사용자 발화·공개 게시는 아직 미완료.

실제 발화 정확도 및 실차는 미검증. 합성 주유 오인식 한계를 유지하며 전체 음성 품질 완료로 표시하지 않는다.
