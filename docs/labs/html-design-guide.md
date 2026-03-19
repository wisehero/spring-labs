# Lab HTML 디자인 가이드

모든 Lab HTML 문서가 공유하는 디자인 시스템 규칙이다. 새 Lab HTML을 만들거나 기존 문서를 수정할 때 이 가이드를 따른다.

---

## 1. 색상 팔레트

다크 테마 전용. 라이트 테마는 없다.

### 기본 색상

| 변수 | 값 | 용도 |
|------|------|------|
| `--void` | `#08090e` | 가장 어두운 배경 |
| `--surface` | `#11131a` | 카드 배경 |
| `--surface2` | `#191c27` | 툴바, 탭 바 배경 |
| `--border` | `#2a2f42` | 모든 테두리 |
| `--text` | `#c8cdd8` | 본문 텍스트 |
| `--text-bright` | `#eef0f6` | 제목, 강조 텍스트 |
| `--dim` | `#9ca3b0` | 부가 설명, 레이블 |

### 시맨틱 색상

각 색상에 10~12% 투명도의 dim 변형이 있다 (배경용).

| 색상 | 값 | dim 변형 | 의미 |
|------|------|----------|------|
| `--cyan` | `#00d4ff` | `rgba(0,212,255,0.12)` | 정보, 활성 상태, 기본 강조 |
| `--green` | `#00e68a` | `rgba(0,230,138,0.10)` | 성공, 통과, 최적화된 결과 |
| `--red` | `#ff4d6a` | `rgba(255,77,106,0.10)` | 실패, 에러, 위험 |
| `--amber` | `#ffb800` | `rgba(255,184,0,0.12)` | 경고, 대안, 주의 |
| `--purple` | `#a78bfa` | `rgba(167,139,250,0.12)` | 보조 강조, 단계 번호 |

### 색상 사용 규칙

- **실험 결과**: 성공=green, 실패=red, 주의=amber, 정보=cyan
- **카드 배경**: color-dim 변형 사용 (e.g., `--cyan-dim`)
- **텍스트 강조**: 원색 그대로 사용 (e.g., `--cyan`)
- **테두리 호버**: `rgba(color, 0.3)`

---

## 2. 타이포그래피

### 폰트 패밀리

| 폰트 | 용도 | Google Fonts weight |
|------|------|---------------------|
| **Noto Sans KR** | 본문, 제목, UI 텍스트 | 400, 500, 700, 900 |
| **DM Serif Display** | 대형 숫자, 메트릭 값 | 400 |
| **D2Coding** | 코드, 기술 레이블, 배지 | - |

### 크기 체계

| 요소 | 크기 | weight |
|------|------|--------|
| masthead h1 | `clamp(32px, 5vw, 72px)` | 900 |
| metric 숫자 | 36~40px (serif) | 400 |
| section-title | 22px | 700 |
| 카드 제목 (h3) | 15~16px | 700 |
| 본문 | 13~15px | 400 |
| 레이블/배지 | 10~11px (uppercase, monospace) | 700 |

### 레이블 스타일

모든 레이블(배지, 태그, sec-label)은 동일한 패턴을 따른다:
- `font-family: 'D2Coding', monospace`
- `font-size: 10~11px`
- `text-transform: uppercase`
- `letter-spacing: 0.08~0.1em`
- `border-radius: 999px` (pill 형태)
- `padding: 4~5px 10~14px`

---

## 3. 페이지 구조

### 전체 레이아웃

```
body (background: --void)
└── .page-wrap (max-width: 1080px, margin: 0 auto, padding: 0 28px 80px)
    ├── .masthead
    ├── .section (반복)
    │   ├── .sec-label
    │   ├── .section-title
    │   ├── p (설명)
    │   └── (콘텐츠: 카드, 테이블, 다이어그램 등)
    └── .closer
```

### Masthead

페이지 최상단. 모든 Lab에 존재한다.

```html
<div class="masthead">
  <div class="masthead-tag">Lab XX</div>
  <h1>제목의 <em>강조 부분</em></h1>
  <p class="masthead-sub">한 줄 설명</p>
</div>
```

- padding: `80px 0 60px`
- `border-bottom: 1px solid var(--border)`
- `margin-bottom: 64px`
- `.masthead-tag`: pill 배지, cyan 배경, 맥동 애니메이션 (::before)
- `<em>`: CSS gradient 텍스트 (cyan→purple, cyan→green 등 Lab별 변형)

### Section

```html
<div class="section">
  <div class="sec-label">0X · 섹션 이름</div>
  <h2 class="section-title">제목</h2>
  <p>설명 (max-width: 640px, color: --dim)</p>
  <!-- 콘텐츠 -->
</div>
```

- `margin-bottom: 72px`
- `padding-top: 64px` (첫 섹션 제외)

### Closer

페이지 마지막. 핵심 메시지를 한 줄로 요약한다.

```html
<div class="closer">
  <h2>핵심 한 줄 요약</h2>
  <p>부연 설명</p>
  <div class="closer-tags">
    <span>태그1</span>
    <span>태그2</span>
  </div>
</div>
```

---

## 4. 컴포넌트

### 실험 카드 (exp-card)

```html
<div class="exp-grid">
  <div class="exp-card green">
    <div class="exp-header">
      <span class="exp-badge">METHOD X-Y</span>
      <h3>실험 제목</h3>
    </div>
    <div class="exp-body">
      <p>설명</p>
      <div class="exp-result">결과 (monospace)</div>
    </div>
  </div>
</div>
```

- `.exp-grid`: `grid-template-columns: 1fr 1fr`, `gap: 20px`
- 색상 클래스: `red`, `green`, `amber`, `cyan`, `purple`
- `.exp-header`: 색상별 dim 배경
- `.exp-result`: monospace, 어두운 배경, 색상별 좌측 테두리

### 메트릭 카드 (metric-card)

```html
<div class="metrics-row">
  <div class="metric-card cyan">
    <div class="metric-label">레이블</div>
    <div class="metric-value">42</div>
    <div class="metric-desc">설명</div>
  </div>
</div>
```

- `.metrics-row`: `grid-template-columns: repeat(4, 1fr)`, 900px 이하에서 `repeat(2, 1fr)`
- `::before`: 상단 2px gradient 라인 (색상에 맞춤)
- `.metric-value`: serif 폰트 (DM Serif Display), 36~40px

### 비교 카드 (compare-card / cmp-card)

두 가지 전략이나 옵션을 나란히 비교할 때 사용한다.

```html
<div class="compare-grid">
  <div class="cmp-card">
    <div class="cmp-header red">
      <span class="cmp-badge">A 방식</span>
      <h3>제목</h3>
    </div>
    <div class="cmp-body">
      <div class="cmp-item">
        <div class="ci-icon" style="color:var(--red);">●</div>
        <div class="ci-text">설명</div>
      </div>
    </div>
  </div>
</div>
```

### 코드 탭 (code-tabs)

```html
<div class="code-tabs">
  <div class="code-tab-bar">
    <button class="code-tab active" onclick="switchTab(0)">탭1</button>
    <button class="code-tab" onclick="switchTab(1)">탭2</button>
  </div>
  <div class="code-tab-panel active" id="codePanel0">코드 내용</div>
  <div class="code-tab-panel" id="codePanel1">코드 내용</div>
</div>
```

- 활성 탭: cyan 색상, 하단 2px cyan 테두리
- 패널 배경: `#0c0e14`
- 좌측 3px 테두리 (색상 코딩)

#### 구문 강조 클래스

| 클래스 | 용도 | 색상 |
|--------|------|------|
| `.kw` | 키워드 (fun, val, if) | purple 계열 |
| `.fn` | 함수명 | cyan 계열 |
| `.st` | 문자열 | green 계열 |
| `.cm` | 주석 | dim |
| `.tp` | 타입 | amber 계열 |
| `.an` | 어노테이션 (@) | green 계열 |
| `.num` | 숫자 | amber 계열 |

### 단계 흐름 (step-flow)

```html
<div class="step-flow">
  <div class="step-item danger">
    <div class="step-num">1</div>
    <div class="step-body">
      <h4>단계 제목</h4>
      <p>설명. <code>인라인 코드</code> 사용 가능.</p>
    </div>
  </div>
</div>
```

- `.step-num`: 48x48px, border-radius 14px, serif 폰트 20px
- 상태 클래스: `danger` (red 좌측 테두리), `success` (green)

### Best Practice 카드 (bp-card)

```html
<div class="bp-grid">
  <div class="bp-card">
    <div class="bp-icon">아이콘</div>
    <h4>제목</h4>
    <p>설명</p>
  </div>
</div>
```

- `.bp-grid`: `repeat(2, 1fr)` 또는 `repeat(3, 1fr)`
- hover: `translateY(-3px)`, border-color 변경

### 테이블

```html
<table class="myth-table">
  <thead><tr><th>헤더1</th><th>헤더2</th></tr></thead>
  <tbody>
    <tr><td class="myth-wrong">잘못된 내용</td><td class="myth-right">올바른 내용</td></tr>
  </tbody>
</table>
```

- `border-collapse: collapse`, surface 배경
- `<thead>`: surface2 배경
- `<th>`: monospace 11px uppercase
- `<td>`: 13px, hover 시 `rgba(cyan, 0.02)` 배경
- 상태 클래스: `.myth-wrong` (red), `.myth-right` (green)

---

## 5. 인터랙션

### 시뮬레이터

각 Lab에 특화된 인터랙티브 요소. 공통 패턴:

```html
<div class="sim-panel">
  <div class="sim-toolbar">
    <div class="sim-controls">
      <button class="sim-btn" onclick="action()">버튼</button>
    </div>
  </div>
  <div class="sim-visual">
    <!-- 시각화 영역 -->
  </div>
  <pre class="sim-output" id="simOut"></pre>
</div>
```

- `.sim-btn`: 12~13px, weight 700, border-radius 8px
- `.sim-btn:hover`: `translateY(-1px)`
- `.sim-btn.active`: 색상별 배경

### 호버 효과

| 대상 | 효과 |
|------|------|
| 카드 | `border-color: rgba(cyan, 0.3)`, `translateY(-2~-3px)` |
| 버튼 | 배경색 변경, `translateY(-1px)` |
| 테이블 행 | `background: rgba(cyan, 0.02)` |
| 코드 탭 | `color: var(--text-bright)` |

### 애니메이션

| 이름 | 효과 | 지속 시간 | 용도 |
|------|------|----------|------|
| `pulse` | opacity 맥동 | 2s infinite | masthead-tag 점 |
| `entrySlideIn` | Y축 슬라이드 + 스케일 | 0.3s ease | 캐시 엔트리 등장 |
| `hitFlash` | 배경색 깜빡임 | 0.5s | 캐시 히트 |
| `evictAnim` | 스케일 축소 + 페이드아웃 | 0.5s forwards | 캐시 퇴거 |

---

## 6. 반응형

### 브레이크포인트

| 브레이크포인트 | 변경 사항 |
|---------------|----------|
| **900px** | 2열 그리드 → 1열, flex-direction: column, 화살표 90° 회전 |
| **768px** | 4열 메트릭 → 2열 |
| **600px** | padding 축소 (28px→14px), masthead padding 축소, bp-grid 1열, 테이블 폰트 12px |

### 모바일 규칙

- 모든 그리드는 600px 이하에서 1열로 전환
- 최소 폰트: 13px (본문), 10px (레이블)
- 테이블: `overflow-x: auto`로 가로 스크롤 허용

---

## 7. Lab별 고유 컴포넌트

각 Lab은 공통 시스템 위에 실험 주제에 맞는 고유 컴포넌트를 추가한다.

| Lab | 고유 컴포넌트 | 설명 |
|-----|-------------|------|
| 00 | `.timeline`, `.alt-card`, `.caution` | 타임라인, 대안 카드, 주의 블록 |
| 01 | `.proxy-diagram`, `.proxy-layer`, `.proxy-vis` | 프록시 호출 시각화 |
| 02 | `.layer-flow`, `.flush-vis`, `.fv-mode` | FlushMode 레이어 다이어그램 |
| 04 | `.concept-card`, `.concept-diagram`, `.tx-box` | 트랜잭션 상태 박스 |
| 05 | `.compare-row`, label/value 쌍 | 락 전략 비교 |
| 06 | `.cache-entry`, `.zone-box`, `.flow-node` | 캐시 시뮬레이터, 히트/미스 애니메이션 |
| 07 | `.compare-card` 변형, `.code-tabs` | fetch 전략 비교, 코드 탭 |

---

## 8. 체크리스트

새 Lab HTML을 만들 때 확인할 항목:

- [ ] `:root` 변수가 표준 팔레트와 동일한가
- [ ] Google Fonts에서 Noto Sans KR, DM Serif Display, D2Coding을 로드하는가
- [ ] `.page-wrap` > `.masthead` > `.section` 순서를 따르는가
- [ ] `.masthead-tag`에 Lab 번호가 있는가
- [ ] 각 섹션에 `.sec-label`과 `.section-title`이 있는가
- [ ] 실험 카드의 색상이 결과 의미와 일치하는가 (성공=green, 실패=red)
- [ ] 엔드포인트가 실제 컨트롤러 라우트와 일치하는가
- [ ] 900px, 600px 브레이크포인트에서 레이아웃이 정상인가
- [ ] `.closer`로 페이지를 마무리하는가
