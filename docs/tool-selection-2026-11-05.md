# 음성 차계부 — 외부 도구 검토 및 선택

확인일: 2026-11-05 KST. 사용자가 제공한 11개 저장소의 공식 README와 저장소 메타데이터를 확인했다. 아래 평가는 이 프로젝트의 짧은 제출 일정과 Android/Pleos 환경을 기준으로 한 판단이며, 저장소의 성능·보안·한국어 정확도를 실측한 평가가 아니다.

## 선택 결과

| 저장소 | 확인한 용도 | 이번 결정과 이유 |
| --- | --- | --- |
| [VoiceStudio](https://github.com/debpalash/VoiceStudio) | 로컬 음성 합성·전사·더빙·음성 제작 앱. Android 미리보기와 별도 음성 엔진도 제공 | 미도입. Pleos에 바로 붙이는 STT SDK로 확인된 것이 아니며 모델·실행환경·한국어·지연 검증이 별도로 필요하다. 오프라인 전사가 꼭 필요해질 때 후보 엔진만 검토한다. |
| [Ponytail](https://github.com/DietrichGebert/ponytail) | 필요한 동작을 최소 구현으로 완성하는 개발 스킬 | **설치·범위 결정에 적용.** 기존 플랫폼 기능 우선, 새 백엔드·대규모 프레임워크·미래 기능 제외. 금액 검증·실패 처리·접근성·필요 테스트는 유지한다. |
| [Cloudflare security-audit-skill](https://github.com/cloudflare/security-audit-skill) | 여러 단계와 독립 검증을 거치는 소스 보안 감사 스킬 | 코드가 생긴 뒤 재검토. 현재 감사 대상 앱 코드가 없어 전체 절차는 실행하지 않았다. Phase 4에서 키·마이크·저장·외부 전송·exported component를 먼저 점검하고, 서버를 도입하면 전문 감사를 검토한다. |
| [ECC](https://github.com/affaan-m/ECC) | 에이전트·스킬·훅·규칙·메모리를 포함하는 종합 개발 도구 | 미도입. 이번 작업에 필요한 작은 스킬과 기존 개발 환경으로 시작한다. 전체 설치로 작업 규칙과 훅을 중복시키지 않는다. |
| [Impeccable](https://github.com/pbakaus/impeccable) | 제품 UI 기획·비평·접근성·상태·네이티브 화면 개선 스킬 | **프로젝트에 설치.** 앱 UI가 생기면 기록 확인과 월별 합계 화면에 적용한다. 현재는 설치 준비 완료이며 UI 감사나 렌더 검증을 했다는 뜻은 아니다. |
| [HyperFrames](https://github.com/heygen-com/hyperframes) | HTML/CSS/미디어를 MP4로 렌더링하는 영상 제작 도구 | 보류. 기본 시연은 실제 앱 화면 녹화로 만든다. 앱이 완성되고 소개 영상 편집이 필요하면 재평가한다. 현 CLI는 Node 22 이상을 안내하며, 기존 확인한 Node 20 환경과도 별도 조정이 필요하다. |
| [Agent-Reach](https://github.com/Panniantong/Agent-Reach) | 여러 웹·소셜·영상 플랫폼의 읽기·검색을 돕는 CLI | 미도입. 현재 조사에는 기존 웹 검색과 공식 문서 접근으로 충분하다. 앱 핵심 기능과 직접 관련이 없다. |
| [Ghidra](https://github.com/NationalSecurityAgency/ghidra) | 바이너리 분석·디컴파일 등 역공학 프레임워크 | 미도입. 자체 Kotlin 앱과 공식 SDK 연동에 필요한 도구가 아니다. |
| [taste-skill](https://github.com/Leonxlnx/taste-skill) | 웹사이트 시각 방향과 브라우저 검증을 위한 디자인 스킬 | 미도입. 네이티브 앱의 작업 완료 중심 UI에는 이번에 고른 Impeccable을 우선하고, 디자인 스킬을 중복 적용하지 않는다. |
| [FreeLLMAPI](https://github.com/tashfeenahmed/freellmapi) | 여러 제공자의 무료 구간·키·모델을 묶는 라우터 | 미도입. 하나의 지출 문장을 처리하려고 모델 라우터를 추가할 필요가 없다. README도 개인 실험용임을 명시하며, 무료 한도·모델 전환을 제출 시연의 전제로 삼지 않는다. |
| [DeepInfer](https://github.com/deepseek-ai/DeepInfer) | DeepSeek 모델용 대규모 추론 프레임워크. 지정 NVIDIA GPU/Huawei NPU와 Linux 등 요구 | 미도입. Mac에서 차량용 앱을 만드는 이번 범위와 맞지 않는다. |

## 실제 설치 범위

공식 `skill-installer`의 다운로드 방식으로 필요한 스킬 디렉터리만 프로젝트의 `.agents/skills`에 설치했다. 전역 설정·자동 훅·브라우저 세션·외부 AI 서버는 구성하지 않았다. 설치된 스킬은 다음 턴부터 프로젝트 스킬로 사용할 수 있다.

| 스킬 | 고정한 revision | 설치 경로 |
| --- | --- | --- |
| Ponytail | `96608e078b2d5ea607c5c3b8b5a54d1c1fba6846` | `.agents/skills/ponytail/` |
| Impeccable | `c8891a9ae865d6ee311836ae5199bfce0fe115b1` | `.agents/skills/impeccable/` |

출처와 revision은 `.agents/skills/sources.json`에 기록했다. 각 디렉터리에 원본 라이선스도 보관했다. Ponytail은 MIT, Impeccable은 Apache-2.0으로 저장소에 표시되어 있다. 다른 도구나 음성 모델을 이후 포함하게 되면 그 버전과 모델의 별도 조건을 다시 확인한다.

이번에 적용한 Ponytail 원칙: 작업에 필요한 최소 구현을 선택하되 금액 정확성, 데이터 보관, 입력 검증, 오류 처리, 실제 장치 검증을 줄이지 않는다. 범위를 두 화면·한 차량·로컬 기록·한 음성 경로로 제한하는 일정에 반영했다.

## SDK와 음성 경로에 관한 새 확인 사항

[프로젝트 생성 및 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)의 현재 안내:

- Playground 가입 후 프로젝트를 생성한다.
- Vehicle State와 NaviHelper는 신청 없이 바로 사용 가능하다고 안내되어 있다.
- FusedLocation, TextToSpeech, SpeechToText, Vehicle Control은 준비 중으로 안내되어 있으며, 해당 상태에서는 신청 버튼이 없다.
- “신청 필요”로 표시되는 항목은 신청 후 승인을 받는 절차다. 모든 SDK가 승인 대기 대상인 것은 아니다.
- 실제 계정 화면과 에뮬레이터에서의 지원은 별도로 확인해야 한다. STT/TTS 문서와 버전이 있다는 사실만으로 서비스 이용 가능성을 단정하지 않는다.

사용자는 사이트에서 가입·프로젝트·SDK 상태를 확인 중이다. 계정 키나 시크릿을 채팅에 복사할 필요는 없다. 음성 경로 결정과 최소 실행 검증은 수정 일정의 Phase 1에서 수행한다.
