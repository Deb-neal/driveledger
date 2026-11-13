# 차계부

차 안에서 잊기 전에 음성으로 차량 지출을 기록하고, 이번 달 기록된 차량 비용을 확인하는 앱.

**Phase 3의 앱 마이크 → 금액·항목 확인 → 명시적 저장 경로를 연결했습니다.** 기본 빌드는 오프라인 음성 입력이며, `-PlocalOnlineVoice=true` debug 빌드에서 OpenAI 온라인 테스트 경로를 사용할 수 있습니다. 두 빌드는 버튼 문구로 구분합니다. Vehicle SDK로 최근 P 상태가 확인되어야 입력·수정·삭제할 수 있습니다. 11/11 실제 사용자가 온라인 발화 5건의 정확한 입력을 확인했으며, 전체 정확도 평가·실차 검증 및 제출용 release는 아직 미완료입니다. [Phase 3 검증 기록](docs/phase-3-validation.md).

11/7 대시보드 개선: Financy 레퍼런스를 바탕으로 밝은 표면과 파란 강조색, 월 지출·건수·최대 항목 요약, 실제 기록에 따른 항목별 도넛 그래프와 최근 내역을 구성했습니다. 입력·확인 화면과 야간 색상도 함께 맞췄습니다. [대시보드 검증 결과](docs/dashboard-validation-2026-11-07.md).

## 기록 화면 사용 방법

1. `P 상태 확인 · 입력할 수 있어요` 표시 후 `새 지출 기록하기` → `마이크로 입력`를 누릅니다. 처음 권한을 허용한 경우 버튼을 다시 눌러 시작합니다.
2. 녹음 중 안내가 나오면 `오늘 주차비 삼천 원`처럼 오늘 지출 한 건을 말하고 `말하기 끝내기`를 누릅니다(최대 8초). 인식문과 금액·항목이 나타나면 직접 확인·수정합니다. 직접 문장을 입력하고 `금액·항목 읽기`를 누르거나 아래 필드에 바로 입력해도 됩니다.
3. `확인하고 저장`을 누르면 기기의 SQLite DB에 저장되고 해당 월 총액·항목별 합계·최근 내역에 반영됩니다.
4. 내역을 눌러 수정하거나 삭제합니다. 이전 달·다음 달로 월별 기록을 확인할 수 있습니다.

- 녹음/변환 중 저장은 잠깁니다. 취소·화면 이탈·오디오 포커스 상실·P 상태 미확인 시 진행 중인 음성을 폐기합니다. 인식 결과는 자동 저장하지 않습니다.
- 차량 상태는 1초 간격으로 조회하고 3초보다 오래된 값은 허용하지 않습니다. P 이외/미상에서는 수동 입력도 막으며 일반 휴대폰에 허용 예외는 없습니다. 실차 주행 규제 인증을 의미하지 않습니다.
- 기본 오프라인 빌드는 기기 내에서 처리합니다. 온라인 테스트 빌드는 첫 사용 안내에 동의한 뒤 최대 8초 음성을 Mac 중계 서버를 거쳐 OpenAI로 전송합니다. 앱/중계 서버는 녹음 파일을 저장하지 않습니다. 취소하면 결과를 무시하지만 이미 전송한 요청을 되돌리지는 못합니다.
- 원 단위 정수만 저장합니다. 한 건당 1~999,999,999원, 합계는 64비트 정수입니다.
- `삼천`, `3천`, `3000`, `3,000` 등 제한된 금액 표기를 지원합니다. 불명확한 금액·항목, 복수 지출, 환불·범위·계산식은 자동 확정하지 않습니다. 파서는 자유 대화나 모든 한국어 숫자를 지원하지 않습니다.
- 신규 기록은 기기 시간대 기준 오늘만 지원합니다. 기존 기록 수정은 원래 날짜를 유지합니다. 자정이 지나면 새 기록을 다시 시작해야 합니다.
- 월 합계는 해당 월 전체 기록 기준이며, 목록은 최근 20건입니다. 더 오래된 같은 달 내역을 펼치는 기능은 아직 없습니다.
- 입력 취소는 저장하지 않습니다. 저장 중 중복 클릭을 차단하고, 같은 기록 식별자의 재시도도 중복 삽입하지 않습니다.
- 저장된 지출에는 날짜·항목·금액만 기록하며, 차량 주행거리는 아직 첨부하지 않습니다. 원문 문장·음성 파일을 DB에 저장하지 않습니다.
- 앱 삭제/데이터 초기화 시 기록이 사라집니다. 계정·클라우드 동기화·내보내기는 제공하지 않습니다.

## 개발 환경

- Kotlin / Android Gradle Plugin 9.0.1 (내장 Kotlin)
- Gradle Wrapper 9.2.1 / JDK 21로 검증, JVM bytecode 17
- minSdk 28 / compileSdk 36.1 / targetSdk 34
- Pleos Vehicle SDK 2.3.2 / Pleos Connect Emulator 2.1.0 (API 34, Mac ARM64)
- 검증한 에뮬레이터 전체 화면: **2560×1440** (앱은 차량 UI의 분할 영역에 표시)
- `local.properties`에 `sdk.dir=<로컬 Android SDK 경로>` 설정

```sh
python3 scripts/fetch-asr.py
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start --user current -n com.iancompany.voiceledger/.MainActivity
```

여러 기기가 있으면 `adb -s <serial>`을 사용합니다. Android Studio에서는 루트 폴더를 프로젝트로 열면 됩니다.

## Mac 연결 온라인 테스트 (debug 전용)

현재 설치 테스트 버전은 `0.3.0-phase3-online-test`이며 버튼은 **온라인으로 말하기**입니다. 일반 `마이크로 입력` 버튼은 오프라인 빌드입니다. 합성 비교 성공을 실제 사용자 발화 정확도 완료로 보지 않습니다.

1. Git 제외 `.env.local`에 `OPENAI_API_KEY=...`를 설정합니다. 키를 APK·로그·저장소에 넣지 않습니다.
2. Mac에서 `python3 scripts/local-voice-relay.py`를 실행한 채 유지합니다. 인터넷 연결이 필요합니다.
3. 다음 명령으로 개발용 연결과 APK를 준비합니다.

```sh
adb reverse tcp:18765 tcp:18765
./gradlew -PlocalOnlineVoice=true :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start --user current -n com.iancompany.voiceledger/.MainActivity
```

서버는 `127.0.0.1`에만 열며 한 번에 한 요청, 프로세스당 최대 40회입니다. 제한 도달 시 실패 안내가 나옵니다. Mac 서버 종료/에뮬레이터 재시작 후에는 서버와 `adb reverse`를 다시 확인합니다. 기기/에뮬레이터의 루프백→Mac 구간만 개발용 HTTP이며 OpenAI 구간은 HTTPS입니다. LAN이나 인터넷에 이 개발 서버를 공개하지 않습니다.

**이 구성은 제출용 서버가 아닙니다.** release에는 로컬 온라인 모드가 비활성화됩니다. 심사용 온라인 APK를 만들려면 별도 인증·사용량 제한을 갖춘 HTTPS 서버와 최종 APK 설치 검증이 필요합니다. [비교와 검증 기록](docs/online-stt-evaluation-2026-11-10.md).

로컬 서버 검사: `python3 -m unittest discover -s scripts -p 'test_*.py'`. `OnlineVoiceTest`는 `-e liveOnline true`를 명시할 때만 합성 음성 한 건으로 실제 API를 호출합니다. 일반 `VoiceInputTest`는 온라인 빌드에서도 오프라인/가짜 디코더로 회귀 검사하며 유료 API를 호출하지 않습니다.

## Phase 1 진단 코드 (제품 화면과 분리)

기존 진단 화면은 외부에 공개하지 않는 `ProbeActivity`로 보존했습니다. 런처는 새 차계부 `MainActivity`입니다. 진단 화면의 동작은 instrumentation 테스트에서 재검증할 수 있습니다.

- Vehicle SDK의 기어·누적 주행거리 조회 결과 또는 조회 오류 표시
- Android RecognitionService 존재 여부 확인
- Whisper tiny multilingual int8 + sherpa-onnx 2.0.1 오프라인 음성 테스트. 마이크는 버튼을 누르고 권한을 허용한 뒤에만 시작
- 최대 8초 녹음, 말하기 끝내기 또는 취소 가능. 화면 이탈 시 녹음 중지, 폐기된 작업의 결과 무시
- 실제 기기는 최근 P 상태를 확인해야 음성 테스트 가능. 에뮬레이터만 개발 검증 예외
- 차량 값은 에뮬레이터의 시뮬레이션 데이터이며 실제 차량 검증 결과가 아님
- 진단 화면은 음성 원본·인식 문장·지출을 파일이나 서버에 저장하지 않음. 음성 인식은 기기 내부에서 처리하며 API 키가 필요하지 않음
- 한국어 항목·금액 오인식 사례가 있어 최종 앱에서도 사용자 확인·수정 후 저장해야 함

Pleos STT/TTS는 사용자 계정에서 Coming Soon으로 확인했습니다. 인증키 발급을 음성 사용 승인으로 취급하지 않습니다. 현재 확인용 앱에는 클라이언트 ID·시크릿이 필요하지 않습니다.

## 제출물

**2026-11-13 소스 공개 준비:** 이 저장소는 개발 중인 소스 공개본입니다. 서명 release APK·시연 영상·독립 음성 서버는 아직 준비 중이며, 저장소 게시만으로 공모전 접수 완료를 의미하지 않습니다.

### release 서명 준비

루트의 `release-signing.properties`가 있으면 release 빌드에 별도 서명키를 사용합니다. 이 파일과 `.signing/`은 Git에서 제외합니다. 본인 키를 사용하는 개발 환경에서 아래 항목을 설정합니다(예시 값은 실제 비밀번호가 아닙니다).

```properties
storeFile=.signing/driveledger-release.jks
keyAlias=driveledger
storePassword=YOUR_LOCAL_PASSWORD
keyPassword=YOUR_LOCAL_PASSWORD
```

`./gradlew :app:assembleRelease`로 빌드합니다. 서명 설정이 없으면 unsigned APK가 생성되므로 제출할 수 없습니다. 서명키와 설정은 별도 안전한 위치에 보관해야 이후 같은 앱을 업데이트할 수 있습니다. APK·저장소에 포함하지 않습니다.

현재 release의 온라인 모드는 아직 연결 전입니다. **서명 성공만으로 제출 준비가 끝난 것은 아니며**, 독립 음성 서버 연결 및 해당 APK의 실제 설치·핵심 동작 검증 후 최종본을 배포합니다.

현재 APK는 `0.3.0-phase3-online-test`, 약 144MB의 개발용 debug APK이며 제출용 release가 아닙니다. 모델이 포함되어 GitHub 일반 파일 업로드 한도를 넘으므로 최종본도 용량에 따라 GitHub Releases를 사용합니다. 최종 산출물은 `apk/팀명_앱이름_release.apk` 또는 GitHub Releases에 배치할 예정입니다.

- [Phase 1 검증 결과와 남은 제약](docs/phase-1-validation.md)
- [Phase 2 검증 결과](docs/phase-2-validation.md)
- [음성 엔진 출처·라이선스](docs/third-party-notices.md)
- [일정](docs/submission-plan.md)
- [도구 검토](docs/tool-selection-2026-11-05.md)
- [제품 기획](docs/product-brief.md)

공식 안내: [Pleos 환경 구성](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Vehicle SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup).

## 에뮬레이터 실행과 재검증

생성한 AVD 이름은 `DriveLedger_Pleos_21`입니다. API 34 / 4GB RAM / Mac ARM64 이미지이며 공식 이미지 SHA-1을 확인했습니다. 일반 휴대폰 AVD를 대체하지 않습니다.

```sh
# 자동 검사에서는 호스트 마이크를 끄고 실행했습니다.
"$ANDROID_HOME/emulator/emulator" -avd DriveLedger_Pleos_21 -no-snapshot -no-audio -timezone Asia/Seoul
# 이미 실행 중인 에뮬레이터에 설치 후, 현재 사용자를 확인합니다.
adb shell am get-current-user
# 이미지가 시작 옵션의 시간대를 덮어쓴다면 개발용 에뮬레이터 시간대를 맞춥니다.
adb shell cmd alarm set-timezone Asia/Seoul
# 이 Pleos 환경의 운전자 사용자는 10입니다. 다른 환경에서는 반환된 번호를 사용하세요.
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 10 -w -e class com.iancompany.voiceledger.OfflineSpeechTest com.iancompany.voiceledger.test/androidx.test.runner.AndroidJUnitRunner
```

`ANDROID_HOME`은 로컬 Android SDK 경로로 설정합니다. 음성 인식 검사는 합성 PCM을 사용하며 실제 사람의 마이크 입력을 검증한 것은 아닙니다. 실제 발화 테스트는 에뮬레이터를 오디오 활성 상태로 실행하고 Extended Controls의 마이크 입력을 켠 뒤 앱에서 직접 시작합니다. `-no-audio`로 실행한 세션에서는 사람의 음성이 들어오지 않습니다.

기본 Android 서비스의 재실험은 `PlatformSpeechProbeTest`에 `-e probePlatformSpeech true`를 명시했을 때만 실행합니다. 현재 Pleos에서는 20초 응답 대기로 실패했고 제품 경로로 사용하지 않습니다. 이 실험 실패를 오프라인 경로 성공과 혼동하지 않습니다.

Phase 2 DB·화면 회귀 검사는 다음 명령으로 실행합니다. `ExpenseStoreTest`는 별도 DB를 사용하며, `LedgerFlowTest`는 테스트에서 추가한 기록만 정리합니다. 개발용 에뮬레이터에서 실행하세요.

```sh
adb shell am instrument --user 10 -w \
  -e class com.iancompany.voiceledger.ExpenseStoreTest,com.iancompany.voiceledger.LedgerFlowTest \
  com.iancompany.voiceledger.test/androidx.test.runner.AndroidJUnitRunner
```

`LedgerVisualTest`는 `-e captureLedger true`를 명시한 경우에만 예시 지출 3건을 넣고 촬영한 후 삭제합니다. 화면의 193,000원은 예시 데이터이며 실사용 지출이 아닙니다.

Phase 3 자동 검사는 `VoiceInputTest`를 실행합니다. 이 테스트는 호스트 마이크를 열지 않고 합성 PCM/가짜 소스를 사용합니다. 대상 앱의 `RECORD_AUDIO` 권한과 SDK의 P 상태가 필요하며 생성한 검사 기록만 정리합니다. 실제 발화 검사와 구분합니다.

```sh
adb shell pm grant --user 10 com.iancompany.voiceledger android.permission.RECORD_AUDIO
adb shell am instrument --user 10 -w -e class com.iancompany.voiceledger.VoiceInputTest \
  com.iancompany.voiceledger.test/androidx.test.runner.AndroidJUnitRunner
```
