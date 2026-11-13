# 현대자동차·Pleos 디자인 조사

확인일: 2026-11-07. 공식 페이지와 실제 브라우저 화면을 확인했다. 이번 작업은 참고 자료 조사이며 앱 디자인·선택한 IBM Plex Sans KR은 변경하지 않았다.

## 공식 자료와 확인 사실

1. 현대자동차 한국 홈페이지: https://www.hyundai.com/kr/ko/e
   - 현재 화면에서 밝은 흰색/옅은 웜그레이 바탕, 크게 구분된 제목, 넓은 여백, 선형 서비스 아이콘, 차량 중심의 이미지 구성을 관찰했다. 이는 화면 관찰이며 현대차 전체 제품에 적용되는 규칙으로 일반화하지 않는다.
2. ccNC Seon 디자인과 Hyundai Sans UI (2023-08-18 공식 뉴스): https://www.hyundai.com/worldwide/en/newsroom/detail/0000000287
   - Seon은 자연의 수평선을 모티프로 한 단순하고 평면적인 그래픽 시스템이다. 공통 그리드와 컴포넌트, 정보 위계를 강조한다.
   - Hyundai Sans UI는 Hyundai Sans의 조형을 계승한 차량 UX 서체다. 이 자료의 ccNC와 현재 Pleos 가이드는 별개로 구분한다.
   - 공식 이미지: https://www.hyundai.com/content/dam/hyundai/ww/en/images/newsroom/news-image/2023/08/0818/contents_images/contents-02-230818.jpg
3. 현재 Pleos Typography: https://document.pleos.ai/en/docs/connect/guide/docs-design/foundations/typography
   - 중립적인 스타일과 일관된 읽기 쉬움을 권장한다. 브랜드별 폰트를 지정할 수 있으며 과장된 조형은 지양한다.
   - 시스템 UI 폰트 자료로 Asta Sans를 명시하고 https://fonts.google.com/specimen/Asta+Sans 에 연결한다.
   - 따라서 Hyundai Sans UI가 Pleos 개발 문서의 유일한 지정 서체라고 설명하면 부정확하다. 사용자 선택 IBM Plex Sans KR의 강제 교체 근거도 아니다.
4. Pleos Colors: https://document.pleos.ai/en/docs/connect/guide/docs-design/foundations/colors
   - 기본 색상과 시스템 색상을 나누고 배경·버튼·텍스트·필드 등의 역할을 구분한다. 색상 대비로 정보 위계를 명확하게 하는 목적이다.
5. Pleos Basic button: https://document.pleos.ai/en/docs/connect/guide/docs-design/components/actions/basic-button
   - 버튼 영역/라벨/앞뒤 아이콘, 눌림·비활성·로딩 상태와 크기/위계 예시를 제공한다.
6. Pleos Display Layout: https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/layout
   - 앱 창은 한 개 또는 두 개로 표시될 수 있으므로 대부분 앱은 가로/세로 구성을 지원해야 한다. 시스템 패널과 키보드는 앱을 일시적으로 가릴 수 있다.
   - 오래된 pleos.ai/playground/resources 문서는 디스플레이 분류가 다르므로 현재 document.pleos.ai 문서를 우선한다.
7. 현대자동차 디자인 철학: https://www.hyundai.com/worldwide/ko/company/innovation/design
   - 구조·비율·스타일링, 파라메트릭 픽셀, 심리스 호라이즌 등의 차량 디자인 배경을 설명한다. 이를 앱의 장식 요소로 반드시 복제해야 한다는 뜻은 아니다.

## 차계부 적용 제안 (공식 요구사항이 아닌 디자인 판단)

- 사용자가 선택한 IBM Plex Sans KR을 유지하면서, Asta Sans는 시스템 UI와 비교할 별도 후보로 남긴다.
- 현재 연두색 기록 버튼과 여러 파스텔 색상을 줄이고, 주요 행동에는 일관된 블루 계열을 쓰는 방향을 비교할 만하다.
- 큰 남색 합계 영역의 면적과 행 간격을 재검토해 합계와 목록의 수평 정렬·정보 위계를 강화한다.
- 홈페이지의 대형 홍보 이미지 구조는 제품 화면에 옮기지 않고, 실제 차량 조작의 기준은 Pleos 레이아웃과 컴포넌트 가이드를 우선한다.
- 더 작은 분할 창과 가로/세로 대응은 별도 실측 검증이 필요하다. 현재 에뮬레이터 넓은 창 검사로 전체 준수를 주장하지 않는다.
