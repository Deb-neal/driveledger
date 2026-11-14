# 서드파티 구성 요소 고지

## 현재 제품 경로 (v0.4.0-pleos-stt)

- Pleos SpeechToText SDK 3.3.1 (`ai.pleos.playground:SpeechToText:3.3.1`): 제품 음성 입력 엔진. `Mode.ON_DEVICE`, 한국어. Pleos Maven 저장소(`nexus-playground.pleos.ai`)에서 빌드 시 받으며 저장소에 바이너리를 포함하지 않는다. 사용 조건은 Pleos 개발자 문서·공모전 안내를 따른다. [공식 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro).
- Pleos Vehicle SDK 2.3.2 (`ai.pleos.playground:Vehicle:2.3.2`): P 상태 확인. 동일 저장소에서 받는다.
- 제품 음성 입력은 외부 음성 인식 서비스로 음성을 전송하지 않는다. 앱 자체 manifest에서는 `INTERNET` 권한을 제거했으나, 병합된 release manifest에는 SDK 의존성이 추가한 `INTERNET` 권한이 남아 있다.

## 오프라인 음성 실험 구성 (과거 진단 경로, 제품 기본 엔진 아님)

`ProbeActivity` 진단 코드가 남아 있어 아래 런타임·모델이 현재 APK에도 포함된다(APK 크기의 대부분). 제품 화면의 음성 입력은 이 엔진을 사용하지 않는다.

- sherpa-onnx 2.0.1: Apache-2.0. [원본](https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.1), [라이선스](licenses/sherpa-onnx-LICENSE).
- Whisper tiny multilingual 모델: MIT. OpenAI 모델을 sherpa-onnx용 ONNX int8 형식으로 변환한 배포본. [원본 모델](https://github.com/openai/whisper), [변환본](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c), [라이선스](licenses/Whisper-LICENSE).
- AAR에는 ONNX Runtime 등 네이티브 런타임이 포함된다. [ONNX Runtime 라이선스](licenses/ONNXRuntime-LICENSE). 제출용 release 배포 전에 전체 전이 의존성 고지 목록을 점검한다.
- 정확한 다운로드 URL과 SHA-256은 [asr-assets.json](asr-assets.json)에 고정한다. 바이너리는 Git에 넣지 않고 `python3 scripts/fetch-asr.py`로 받는다. `--verify-only`는 다운로드 없이 검증한다.
- 이 진단 경로는 앱 프로세스 안에서 음성을 처리한다. 과거 온라인 비교(Mac relay·Render → OpenAI)는 v0.4.0에서 빌드 설정으로 비활성화했으며 `server/`·관련 스크립트는 기록용으로만 남아 있다. 현재 동작과 제약은 루트 README를 따른다.
- `androidTest/assets/*.pcm`은 자체 작성한 문장을 macOS Yuna로 합성한 16kHz, mono, signed 16-bit little-endian PCM이다. 실사용자 발화가 아니다. Parking: “오늘 주차비 삼천 원”, Fuel: “오늘 주유 칠만 원”.

이 구성은 Phase 1 기능 검증용이다. 소형 모델은 금액과 항목을 오인식할 수 있으므로 결과를 자동 저장하거나 정확한 지출로 단정해서는 안 된다. 한국어 품질은 실제 발화와 차량 환경에서 추가 검증해야 한다.


## UI 글꼴

- IBM Plex Sans KR Regular / SemiBold: https://github.com/IBM/plex
- 배포 파일 출처: https://github.com/google/fonts/tree/main/ofl/ibmplexsanskr
- SIL Open Font License 1.1. 원본을 수정하지 않고 번들했다. `app/src/main/assets/licenses/IBMPlexSansKR-OFL.txt`에 라이선스 전문이 포함된다.
- 항목 아이콘은 이 프로젝트에서 작성한 VectorDrawable이다. Cashew/Reply/Jetsnack은 디자인 참고이며 그 소스 코드나 에셋을 복제하지 않았다.

- `scripts/fixtures/*.wav`은 비교용 합성 음성 4개이며 실제 사용자 녹음이 아니다. 원본 해시는 `docs/online-stt-fixtures.json`에 고정되어 있다. 11/13 소스 공개 시 새 체크아웃에서도 검사가 가능하도록 기존 비공개 artifacts 경로에서 복사했다.
