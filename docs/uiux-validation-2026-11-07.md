# 차계부 UIUX 개선 검증

2026-11-07. Phase 2의 홈·입력·확인·수정 화면을 재구성했다.

## 적용 범위

- Cashew의 지출 가독성, Reply의 넓은 화면 영역 분리, Jetsnack의 컴포넌트 일관성을 참고했다. 외부 앱 코드는 가져오지 않았다.
- 기존 Android Views 위에서 구성했다. 새 UI 프레임워크 의존성이나 DB 마이그레이션은 없다.
- Pretendard Variable v1.3.9만 에셋으로 번들했다. 원본: https://github.com/orioncactus/pretendard/tree/v1.3.9, SIL OFL 라이선스 동봉. 글꼴 원본은 약 6.7MB다.
- 제목·월 이동 정리, 남색 월 요약과 눈에 띄는 기록 버튼, 날짜별 목록과 오른쪽 금액 정렬, 항목 아이콘, 입력/확인 분리, 문장 예시 입력을 구현했다.
- 예시는 초안만 채운다. 실제 사용자가 저장하기 전 DB에 쓰지 않는다.

## 검증

- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`
- Pleos 2.1 / API 34 ARM64, 2560×1440, user 10에서 설치 및 실행.
- `ExpenseStoreTest`, `LedgerFlowTest`, `LedgerVisualTest` 3개 통과. 저장, 중복 클릭, 화면 재생성, 수정, 취소, 삭제와 월 집계 회귀를 확인했다.
- 본문 24개 색상 조합이 낮/밤 모두 명암비 4.5:1 이상이다. `artifacts/uiux/contrast.json`.
- 단위 테스트 **16개 통과**, Lint **오류 0 / 경고 17**. 새 화면 빌드 및 설치 성공. debug APK 145,167,549 bytes이며 release가 아니다.
- 기본·야간·야간+글씨 1.3배에서 각각 홈/확인/오류 화면을 촬영하고 직접 확인했다. `home-light.png`, `confirm-light.png`, `invalid-light.png` 및 `*-dark.png`, `*-dark-large.png`.
- 1.3배 검사는 Activity의 실제 `configuration.fontScale`이 1.3인지 추가 assertion으로 확인했다. `font-scale-verification.txt`의 테스트 성공이 근거다.
- 스트레스 데이터는 8개 항목 × 999,999,999원 = 7,999,999,992원이다. 총액 한 줄·말줄임 없음·가용 폭 내 렌더 assertion을 통과했다. 많은 내역은 스크롤로 이어진다.
- 빈 상태도 실제 화면에서 확인했다. 테스트 종료 후 로컬 DB는 **0건 / 0원**이며, 야간 모드 꺼짐·글씨 크기 1.0으로 복구했다.
- 크롭된 `home-light-detail.png`, `confirm-light-detail.png`, `home-before-detail.png`는 실제 캡처의 앱 영역을 잘라 비교하기 위한 이미지다. 원본 전체 캡처도 보관한다.

## 산출물 및 제약

`artifacts/uiux/`의 캡처는 실제 Android 화면이다. 193,000원은 주유 70,000원·주차 3,000원·정비 120,000원의 임시 예시이며 촬영 종료 후 제거한다. 스트레스 데이터도 테스트에서 생성한 ID만 제거한다.

음성 연결과 주행 중 입력 제한은 이번 범위가 아니다. 아직 차량에 설치하여 운전 중 사용할 완성품이 아니며, release 서명·실차·사람 발화·사용성 평가는 남아 있다. 실제 스마트폰 환경의 화면은 이번에 검증하지 않았다.


## 후속 글꼴 확정 — IBM Plex Sans KR

사용자가 비교표의 ⑤ IBM Plex Sans KR을 선택했다. 앱의 Pretendard를 제거하고 비교에 사용했던 IBM Plex Sans KR Regular(400) / SemiBold(600) 파일을 적용했다. 본문·입력은 Regular, 제목·금액·주요 버튼은 SemiBold이며 낮/밤 테마의 기본 font family도 통일했다. SIL OFL 라이선스를 앱 assets에 포함했다.

빌드와 Lint 오류 0을 확인했다(기존 경고 17개). 글꼴 변경 후 debug APK는 144,299,396 bytes다. 기존 저장·파서·집계 로직은 변경하지 않았다. 글꼴 변경 후 캡처는 `artifacts/font-update/`에 구분하여 보관한다.

기본 테마와 야간+글씨 1.3배에서 시각 검사 각각 1개를 통과했다. 홈·확인·오류 캡처를 직접 확인했으며 큰 금액의 모든 자릿수가 보인다. 실제 `fontScale=1.3` assertion도 통과했다. 검증 후 시스템 설정을 낮/1.0으로 복원했다. 실차 렌더링은 미검증이다.

## 후속 디자인 시스템 적용 — 글자·간격·상태

사용자가 공유한 7개 디자인 시스템 참고 이미지를 바탕으로 현재 화면을 정리했다. IBM Plex Sans KR과 기존 팔레트, 두 열/세로 배치를 유지했다.

- `LedgerDesign.kt`에서 글자 역할별 크기·굵기·행간, 간격 척도, 16dp 모서리와 버튼 역할을 관리한다. MainActivity의 글자·버튼·입력·항목 선택과 목록 간격이 이를 사용한다.
- 입력란은 기본 테두리, 포커스 강조, 오류 테두리, 비활성 배경/문자색을 공유한다. 입력값을 수정하면 이전 금액 오류 테두리와 네이티브 오류를 해제한다.
- 주요/보조/기록/삭제 버튼의 역할을 구분했다. 저장·삭제 중 해당 버튼에 진행 문구를 표시하고 모든 입력을 비활성화한다. 실패하면 버튼 문구와 조작을 복원한다.
- 빌드 `assembleDebug assembleDebugAndroidTest lintDebug` 성공. Lint 오류 0 / 기존 경고 17개.
- Pleos 2.1/API 34 ARM64에서 `LedgerFlowTest`와 기본 테마 `LedgerVisualTest` 2개 통과. 오류 해제와 저장 중 버튼 비활성/문구 assertion을 기존 저장·수정·취소·삭제 회귀 검사에 추가했다.
- 야간 + 실제 fontScale 1.3에서 시각 검사 1개 통과. 총액 7,999,999,992원의 한 줄 표시·말줄임 없음·가용 폭 assertion 통과. 홈/입력 확인/오류 화면을 캡처해 확인했다. 긴 목록은 스크롤로 이어진다.
- 새 비활성 색상, 오류·포커스 색상 등 10개 낮/밤 문자 조합 명암비는 4.72:1 이상이다. `artifacts/design-system/contrast.json`.

최신 캡처와 로그는 `artifacts/design-system/`에 있다. `*-detail.png`는 실제 앱 영역만 자른 이미지다. 193,000원 및 큰 금액 화면은 테스트용 임시 내역이며 테스트 종료 시 해당 ID만 제거한다. 이번에는 금액 계산·파서 단위 테스트를 다시 실행하지 않았으며 해당 코드도 변경하지 않았다. 실차 및 좁은 창의 시각 검증은 이번 범위에서 수행하지 않았다.

빈 목록과 금액 입력 포커스도 실제 화면에서 확인했다(`home-empty.png`, `amount-focus.png`). 종료 시 낮 모드/fontScale 1.0으로 복원했고, DB는 0건/0원이며 빈 초안도 취소했다.
