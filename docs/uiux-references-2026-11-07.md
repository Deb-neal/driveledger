# 차계부 UI/UX 개선을 위한 GitHub 조사

확인일: 2026-11-07 KST. 사용자 요청은 현재 UI가 만족스럽지 않아 도움이 될 저장소를 찾는 것이다. 이 문서는 조사·적용 제안이며 새 패키지 설치나 화면 교체를 수행한 기록이 아니다. 공식 README·문서를 확인했으며 아래 앱을 빌드하거나 Pleos에서 실행하지 않았다.

## 우선 후보

| 후보 | 출처에서 확인한 내용 | 차계부 적용 제안 및 한계 |
| --- | --- | --- |
| [Cashew](https://github.com/jameskokoska/Cashew) | Flutter로 만든 예산·지출 관리 앱. 저장소 GPL-3.0 표기 | 가계부 기능의 정보 구성과 화면 참고 후보. Kotlin 프로젝트에 직접 넣는 UI 라이브러리가 아니다. 코드/자산 도입과 별개로 사용 흐름을 참고한다. |
| [Google Reply](https://github.com/android/compose-samples/blob/main/Reply/README.md) | 공식 Compose 샘플. 창 크기에 따른 탐색·목록/상세 배치, Material 3와 밝은/어두운 테마 | 가로 차량 창에서 내역과 상세를 배치하는 방법의 우선 참고. 휴대폰/태블릿용 예제이므로 Pleos 규격이나 주행 정책 검증을 대체하지 않는다. |
| [Google Jetsnack](https://github.com/android/compose-samples/blob/main/Jetsnack/README.md) | 공식 Compose 샘플. 커스텀 디자인 시스템·레이아웃·애니메이션 | 색상·컴포넌트·전환을 앱 전체에서 일관되게 구현하는 참고. 쇼핑 앱의 이미지 중심 화면 자체를 차량 가계부에 복제하지 않는다. |
| [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | 디자인 시스템 생성과 UX 지침을 제공하는 에이전트 스킬. Jetpack Compose 스택을 명시 | 새 디자인 방향과 정보 계층을 잡는 보조 후보. 기본 스택은 HTML+Tailwind이므로 Android/Compose를 명시해야 한다. 미설치·효과 미검증이며 화면 품질 보장 도구는 아니다. |
| [Impeccable](https://github.com/pbakaus/impeccable) | critique·audit·polish·layout 등 디자인 검토/개선 기능 | 이미 프로젝트에 설치되어 있다. 기존 화면의 구체적인 문제를 평가하고 개선 결과를 검토하는 데 사용. 이전 구현에 지침을 참고했지만 실제 사용자의 미적 만족 기준을 충족하지 못했다. |
| [Google Car Samples](https://github.com/android/car-samples) | Android Auto·Android Automotive OS의 차량 앱 예제 | 차량 동작·제약을 확인하는 보조 참고. Pleos 전용 UI 키트 또는 차계부의 자동 지원 근거로 취급하지 않는다. |

## 우선 도입하지 않을 후보

- [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet): Android 가계부 참고지만 저장소가 보관 처리되었고 유지보수 중단을 명시한다. 신규 기반 의존성으로 선택하지 않는다.
- [Material Theme Builder](https://github.com/material-foundation/material-theme-builder): 저장소가 보관 처리되어 있다. 활성 개발 도구로 소개하지 않는다.
- [Views용 Material Components](https://github.com/material-components/material-components-android): 공식 README가 maintenance mode이며 Views 신규 기능 출시 계획이 없다고 명시한다. 기존 Android Views에서 단기 개선은 가능하나 새 UI 구현의 우선 후보는 Compose Material 3다.

## 현재 코드와의 관계

현재 MainActivity는 Android 기본 Button/EditText/LinearLayout을 직접 조합한다. Compose나 Material Components 라이브러리는 설치되어 있지 않다. 저장·파싱·집계는 ExpenseStore/ExpenseParser/Expense 등으로 분리되어 있으므로 화면 재설계 시 재사용할 수 있다.

[Android 공식 상호운용 문서](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views)는 기존 Views 앱에 Compose 화면을 추가하는 방식을 지원한다. 다만 이 프로젝트의 AGP·Kotlin 조합과 Pleos에서의 빌드·입력·수명주기는 별도 검증해야 한다.

## 제안하는 다음 순서

1. 실제 차량 앱 창 비율로 홈 시안 2~3개를 만든다. 공통 내용은 기록된 월 합계·항목별 지출·최근 내역이며, 향후 음성 기록 진입의 우선순위를 함께 설계한다. 데모 숫자와 아직 연결되지 않은 음성 상태는 분명히 표시한다.
2. 사용자가 고른 방향으로 글자 크기·간격·색상·버튼·리스트·확인 흐름을 일관되게 설계한다. 현재 화면은 중요한 요소가 상단에 몰리고, 긴 버튼형 내역과 작은 기록 진입 버튼 때문에 목적에 맞는 위계가 약하다는 것이 이번 검토의 판단이다.
3. Compose를 채택한다면 먼저 확인 화면 한 개의 Pleos 실행을 검증한다. 통과한 뒤 홈까지 교체하고 기존 저장·금액 테스트와 새 UI 회귀 검사를 실행한다. 신규 런타임 도입은 이번 조사에서 확정하지 않았다.
4. Impeccable로 시안과 실제 렌더를 비교하고 입력·오류·빈 상태·야간·큰 글씨를 검토한다. UI UX Pro Max는 필요할 때 보완 도구로 추가한다.

권장 조합은 **Cashew의 가계부 참고 + Reply의 가로 화면 구조 + Jetsnack의 컴포넌트 구현 참고 + Impeccable 검토**다. 이 선택은 차계부에 대한 제안이며 각 저장소의 Pleos 적합성이 이미 입증됐다는 뜻은 아니다.


## 적용 결과 (11/7)

사용자의 적용 지시에 따라 Cashew·Reply·Jetsnack의 정보 구성 원칙을 기존 Android Views에서 구현했다. 새 UI 프레임워크를 넣지 않고 화면 구조·글꼴·색상·내역 행·확인 폼을 개선했다. Impeccable로 실제 렌더를 검토했다. UI UX Pro Max는 설치하지 않았다. [검증 기록](uiux-validation-2026-11-07.md).
