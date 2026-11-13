---
name: 차계부
description: 기록한 차량 지출을 한눈에 보여주는 대시보드
colors:
  surface: "#F5F7FB"
  surface_container: "#FFFFFF"
  on_surface: "#18243B"
  on_surface_variant: "#59667C"
  primary: "#2059D9"
  on_primary: "#FFFFFF"
  primary_container: "#EAF0FE"
  outline: "#8491A7"
  error: "#B32632"
  on_error: "#FFFFFF"
  divider: "#E6EBF2"
  field: "#F7F9FC"
  disabled_surface: "#E1E5EE"
  on_disabled: "#59647A"
  chart_fuel: "#2864E7"
  chart_charging: "#159979"
  chart_parking: "#8A62D8"
  chart_toll: "#0798B2"
  chart_maintenance: "#E58335"
  chart_insurance: "#D9577E"
  chart_tax: "#526787"
  chart_other: "#8A97AB"
typography:
  Display:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "56sp"
    fontWeight: 600
    lineHeight: "64sp"
  Metric:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "32sp"
    fontWeight: 600
    lineHeight: "44sp"
  PageTitle:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "32sp"
    fontWeight: 600
    lineHeight: "44sp"
  SectionTitle:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "34sp"
  AmountInput:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "40sp"
    fontWeight: 600
    lineHeight: "52sp"
  Amount:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "34sp"
  Input:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "24sp"
    fontWeight: 400
    lineHeight: "34sp"
  Body:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "20sp"
    fontWeight: 400
    lineHeight: "30sp"
  Label:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "30sp"
  Caption:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  CaptionStrong:
    fontFamily: "IBM Plex Sans KR"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
rounded:
  surface: "16dp"
spacing:
  XS: "8dp"
  S: "12dp"
  M: "16dp"
  L: "24dp"
  XL: "32dp"
  XXL: "40dp"
  Page: "48dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on_primary}"
    rounded: "{rounded.surface}"
    typography: "{typography.Label}"
    padding: "12dp 24dp"
---

## Overview

2026-11-07 사용자가 선택한 Financy Finance Dashboard 소개 이미지의 **밝은 표면·파란 강조색·상단 요약·도넛 그래프와 최근 내역**을 차계부에 적용했다. 기존 남색 요약 패널과 연두색 버튼 방향을 대체한다. 사용자가 선택한 IBM Plex Sans KR과 공통 디자인 기준은 유지한다. 원본 소스·브랜드·은행/수입 기능을 복제하지 않는다.

차량에 쓴 돈을 주차 상태에서 기록하고, 선택한 달의 비용과 비중을 읽는 화면이다. 실제 저장된 내역만 집계한다. 이전 구현의 기록 확인·수정·취소·삭제·재실행 보관 정책을 유지한다.

## Colors

낮에는 옅은 회색 바탕과 흰 표면, 파란색 금액·주요 행동을 사용한다. 밤에는 어두운 중립색 표면과 밝은 파란 강조색으로 시스템 야간 설정을 따른다. 실제 색상은 `res/values/colors.xml`, `res/values-night/colors.xml`의 동일한 역할 이름으로 관리한다.

그래프는 주유·충전·주차·통행료·정비·보험·자동차세·기타의 8가지 고정 색상을 사용한다. 항목별 색은 금액 순위나 데이터 유무에 따라 바뀌지 않는다. 범례에 항목명·원 단위 금액·비율을 함께 표시하며, 내역 아이콘은 파란색으로 통일한다.

## Typography

`LedgerDesign.kt`의 TextRole을 모든 글자·버튼·입력란에 재사용한다. IBM Plex Sans KR Regular 400 / SemiBold 600을 번들한다. 1200dp 이상에서는 1.25배, 시스템 글씨 확대도 반영한다. Display는 자동 축소와 1.15배 행간을 사용해 긴 총액의 모든 자릿수를 표시한다. 나머지 역할은 위의 크기/행간을 사용한다. 금액에는 tabular figures를 적용한다.

## Layout

홈 상단은 앱 제목·월 이동·기록 시작 버튼, 다음은 지출 합계·기록 건수·최대 지출 항목이다. 1000dp 이상에서 요약은 46:20:34, 아래는 그래프/범례 42 : 내역 58로 배치하며 24dp 간격을 둔다. 좁은 창은 세로로 이어지고 전체 화면을 스크롤한다. 기록 버튼은 1200dp 이상에서는 상단 오른쪽, 그 아래는 별도 전체 폭이다.

새 입력·확인은 840dp 이상에서 두 열, 그 아래는 세로다. 수정 화면은 제목·날짜·폼·안내를 최대 960dp의 한 열로 모아 앱 영역 가운데 배치한다. 수정 폼의 반복 소제목을 없애고, 금액·항목 라벨은 Body/보조색으로 표현해 금액을 먼저 읽게 한다. 바깥 여백은 기본 24dp, 1200dp 이상 48dp이며 페이지 상단 32dp다. 표면 내부 여백은 24/32dp, 구성 요소 사이 간격은 Gap 척도를 따른다. 요약 카드는 같은 높이로 맞춘다. 긴 목록·범례는 스크롤로 계속 볼 수 있다.

## Elevation & Depth

장식 그림자와 그라데이션 없이 바탕/표면 차이와 얇은 구분선으로 영역을 나눈다. 내역 행마다 별도의 카드를 겹치지 않는다. 삭제/폐기 대화상자는 native AlertDialog 안에 공통 텍스트·버튼을 배치한다. 팝업 elevation은 4dp, 배경 dim은 0.24로 결정 영역을 구분한다.

## Shapes

공통 표면·버튼·입력 모서리는 16dp다. 아이콘은 직접 작성한 VectorDrawable, 1.8 단위 선이다. 그래프는 Android Canvas로 그리는 실제 비중 도넛이며 외부 차트 라이브러리를 추가하지 않는다.

## Components

- **확인 팝업**: 삭제·입력 취소는 native AlertDialog와 스크롤 가능한 내용 영역을 공유한다. 앱 창 안에서 최대 880dp, 좌우 최소 24dp 여유, 내부 32dp. 제목 PageTitle → 16dp → 설명 Body → 32dp → 버튼 순서. 삭제 확정은 Danger(error/on_error), 취소는 Neutral(field/on_surface). Back·바깥 탭·취소는 기록을 변경하지 않는다.
- **폼 행동**: 취소·저장을 한 줄에 배치하고, 실제 가용 폭이 `560dp × fontScale` 미만이면 순서를 유지하며 세로로 쌓는다. 버튼 사이 가로 16dp/세로 12dp. 수정 화면의 삭제는 32dp 뒤 구분선과 함께 아래 오른쪽으로 분리한다.
- **버튼**: Primary/Secondary/Neutral/Destructive(텍스트)/Danger(확정) 역할. 최소 높이 64dp, 넓은 창 80dp. 눌림 ripple, 포커스 테두리, 비활성 배경/문자를 공유한다. 저장/삭제 중 문구와 비활성 상태를 표시하며 실패 시 복원한다.

- **입력란**: 기본 테두리, 포커스 강조, 오류 테두리·문구·아이콘, 비활성 배경/문자. 금액을 고치면 이전 오류를 해제한다. 예시는 초안만 채우며 저장은 명시적인 확인이 필요하다.
- **요약**: 선택한 달의 전체 기록을 기준으로 한다. 최대 지출 동률은 항목 순서로 결정해 그중 하나를 표시한다. 거래가 없으면 0건·0원·기록 없음이다.
- **그래프**: 전체 월 집계에서 내림차순으로 계산한다. 최근 20건 목록 제한과 무관하다. 한 항목은 전체 원, 빈 달은 그래프 대신 안내다. 아주 작은 비중을 임의로 확대하지 않는다. 비율은 소수 첫째 자리까지 반올림해 합계가 100%와 조금 다를 수 있으며, 양수 0.1% 미만은 그대로 안내한다. 범례는 접근성 설명에 항목·금액·비율을 포함하고 도넛 자체는 비대화형이다.
- **내역**: 최근 20건의 항목·날짜·금액, 얇은 구분선. 행 전체가 수정/삭제 진입점이다. 기본 높이 96dp, 넓은 창 112dp. 날짜는 기기 로컬 시간 정책을 따른다.

## Do's and Don'ts

- 공통 색상·글자·간격·모서리 역할을 재사용한다. Android는 sp/dp로 구현하고 실제 Pleos 화면에서 검증한다.
- 월 이동, 시스템 Back, 초안 복원과 데이터 정확성을 보존한다.
- 없는 수입/잔액/예산/절약 실적, 작동하지 않는 탭·음성 버튼을 추가하지 않는다.
- 임시 시연 데이터를 앱 기본 데이터로 넣지 않는다. 색상만으로 항목이나 오류를 전달하지 않는다.
- 11/10 제품 음성 입력·최근 P 상태 입력 제한을 연결했다. 신규 기록 왼쪽에 시작/끝내기·취소와 상태 안내, 오른쪽에 기존 확인/저장을 배치한다. 녹음·변환 중 직접 입력/저장을 잠그고 실제 사람 발화 검증 여부는 별도로 표시한다.

검증 기록: `docs/dashboard-validation-2026-11-07.md`. 이전 구현 기록은 `docs/uiux-validation-2026-11-07.md`에 보관한다.

- 온라인 테스트 빌드는 “온라인으로 말하기” 버튼과 “OpenAI 온라인 테스트 · Mac 서버 연결 필요”를 표시한다. 최초 녹음 전에 기존 확인 대화상자로 최대 8초 음성의 외부 전송과 확인 후 저장을 안내한다. 서버 오류는 별도로 설명하며 오프라인으로 조용히 전환하지 않는다.
