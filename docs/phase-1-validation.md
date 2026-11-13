# Phase 1 — 실행 환경과 음성 경로 검증

작업일: 2026-11-05 KST. **Phase 1 개발 기반 및 에뮬레이터 실행 검증 완료.** 한국어 정확도 합격이나 실차 검증 완료를 뜻하지 않는다.

## 확인된 사항

- 사용자 Playground 프로젝트 생성 완료. 계정 화면에서 FusedLocation·STT·TTS·Vehicle Control은 Coming Soon.
- Kotlin Android 프로젝트 생성. AGP 9.0.1, Gradle 9.2.1, JDK 21.0.11, compileSdk 36.1, targetSdk 34, minSdk 28.
- Vehicle SDK 2.3.2를 공식 Maven에서 내려받아 의존성 해결·컴파일 완료. 비밀키 없이 가능.
- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` 성공.
- 정책·PCM 테스트 5개 통과: 실제 기기에서 미상·주행·오래된 상태 입력 차단, 에뮬레이터 테스트 허용, 미상/비정상 거리와 정상 0 구분, 무음·짧은 입력·비정상 PCM 차단.
- Lint 오류 0. 개발 확인 화면의 문자열 리소스화·아이콘, targetSdk/AGP 업데이트 알림, 데이터 이전 정책 관련 경고는 최종 UI/저장 구현 시 처리할 항목으로 남아 있다.
- 로컬 Git 초기화. 원격 저장소 생성·소스 업로드는 수행하지 않았다.

## 실행 검증 기록

- 공식 Mac ARM64 이미지 다운로드 및 SHA-1 `24d28eb2f18eba5e87c861a7eb2bdccebb049419` 검증 완료.
- Android SDK 명령행 도구·API 34 설치, Pleos 전용 AVD `DriveLedger_Pleos_21` 생성. 기존 휴대폰 AVD는 유지.
- 이미지의 source.properties를 읽은 SDK 도구가 패키지 ID를 `system-images;android-34;android-automotive;arm64-v8a`로 정규화하여 그 경로에 배치했다. 시스템 이미지 자체를 수정하지 않았다.
- 부팅 fingerprint: `Pleos/crp_avd_arm64/crp_avd_arm64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`.
- 앱 설치·시작 성공. Pleos의 현재 운전자 사용자는 10이며 `am start --user 10`으로 실행해야 했다.
- Vehicle SDK 콜백에서 `GEAR_PARK`, 누적 주행거리 원시 값 `0.0` 수신. 값은 에뮬레이터 데이터이며 임의 fallback이 아니다. 거리 단위 검증은 아직 남아 원시 값으로 표시한다.
- 실제 Android RecognitionService `ai.umos.ambientai/.service.AmbRecognitionService`가 존재한다. 그러나 기본 시작 검사와 합성 PCM 전달 검사에서 결과 콜백을 받지 못했다. 이것은 현재 환경에서의 실패이며 모든 Pleos 기기의 서비스 지원 여부를 단정하는 근거는 아니다.
- APK에 포함한 sherpa-onnx 2.0.1 + Whisper tiny int8 모델의 에뮬레이터 테스트에서 “오늘 주차비 삼천 원” 합성 음성을 “오늘 주차 비 3천원”으로 변환했다. 무음 입력은 빈 결과로 차단했다. `OfflineSpeechTest` 통과.
- 현재 debug APK는 약 142MB(약 135MiB). release APK가 아니며, 최종 배포 용량도 확인 후 GitHub Releases 경로로 제출할 가능성이 높다.
- 호스트 마이크를 끈 `-no-audio` 환경에서 검사했다. 사람의 실제 발화·실차 오디오 검증을 했다고 주장하지 않는다.

## 음성 경로 결정과 정확도 한계

개발용 경로는 **기기 내부 Whisper tiny multilingual int8**로 선택했다. API 키·서버·유료 호출은 필요하지 않다. 자동 저장은 구현하지 않았으며, 다음 단계에서 사용자 확인과 수정이 필수다.

비교 실험은 macOS Yuna가 합성한 문장을 16kHz mono PCM으로 변환해 수행했다. 이는 실제 사용자 발화 테스트가 아니다.

| 입력 (Yuna rate 160) | Whisper tiny 출력 | Whisper base 출력 |
| --- | --- | --- |
| 오늘 주유 칠만 원 | 오늘17만 원 | 오늘 167만원 |
| 오늘 주차비 삼천 원 | 오늘 주차 비 삼천원 | 오늘 주차 비 3000원 |
| 자동차 정비비 십이만 원 | 자동차 정비비 12만 원 | 자동차 정비비 12만 원 |
| 오늘 통행료 오천 원 | 오늘 통행률 5천원 | 오늘 통행류 5천원 |

- 첫 문장에서 두 모델 모두 **금액 오류**가 있었다. 큰 base 모델도 그 오류를 해결하지 못했으므로 용량이 작은 tiny를 개발용으로만 채택했다. 정확도 검증을 통과했다는 결정이 아니다.
- Vosk 소형 한국어 모델도 별도 합성 음성 검사에서 문장이 크게 달라져 미채택했다. Vosk·Whisper base는 최종 앱 의존성이 아니다.
- ARM64 에뮬레이터와 Mac에서 같은 주차비 음성이 각각 “3천원”과 “삼천원”으로 출력되었다. 금액 파서는 이런 표기를 동등하게 처리해야 한다. 해당 테스트는 3천원이라는 의미를 검증하도록 작성했다.
- `PcmAudio`는 짧거나 거의 무음인 신호를 차단한다. 차량 소음을 구분하는 VAD가 아니며 실제 환경에서 임계값·인식 품질을 검증해야 한다.
- Phase 3에서 실제 발화로 금액·항목 정확도를 측정하고, 결과가 부족하면 음성 경로를 교체하거나 지원 문장 범위를 조정한다. 오인식 문장을 임의로 원하는 금액으로 고쳐서 성능으로 보고하지 않는다.

## 재현·검증 산출물

- `python3 scripts/fetch-asr.py --verify-only`: AAR와 모델 4개 파일의 고정 SHA-256 검사 성공.
- 단위 테스트 5개 및 Lint 오류 0. 정적 검사 경고는 남아 있다.
- `OfflineSpeechTest`: 에뮬레이터 합성 음성·무음 검사 성공.
- `MicrophoneLifecycleTest`: 호스트 오디오가 비활성화된 에뮬레이터에서 AudioRecord 시작을 확인하고, Activity가 CREATED 상태로 내려가면 녹음이 해제되는 것을 검증했다. 최종 instrumentation 검사 2개 통과.
- AAOS에서 단순 Back 키는 앱을 종료하지 않아 초기 수동 검사가 목적한 상태 전환을 만들지 못했다. 실제 lifecycle 전환을 검증하는 테스트로 확인했고, 멀티윈도우에서 onPause에도 녹음을 취소하도록 보강했다.
- 확인 화면: `artifacts/phase1/pleos-probe.png`. 최종 앱 디자인이 아닌 개발용 진단 화면이다.
- 기본 Android 음성 서비스 검사는 20초 타임아웃으로 실패했다. 재실험할 때만 `probePlatformSpeech=true`로 실행하도록 분리했다.
- 로컬 증거는 `artifacts/phase1/`에 보관하며 Git에서는 제외한다. 제출할 자료는 추후 선별한다.

## 범위

현재 실행 화면은 개발용 진단 화면이다. 최종 차계부의 디자인, DB·월 집계, 제출용 release APK와 시연 영상은 후속 Phase 범위다. 자동으로 임의의 비용을 저장하거나 차량 상태를 만들어 표시하지 않는다.

Ponytail은 구현 범위와 기존 플랫폼 사용 원칙에 적용했다. Impeccable은 설치되어 있으나 제품 화면의 본격 설계·감사는 아직 수행하지 않았다. 현재 진단 화면은 기본 Android 컴포넌트로 만든 테스트 도구다.

## 출처

- [Pleos 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Vehicle SDK 의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [Vehicle 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Vosk Android](https://alphacephei.com/vosk/android), [한국어 모델](https://alphacephei.com/vosk/models)
