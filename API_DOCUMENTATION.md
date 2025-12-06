# 📚 Novel Insight API 문서

## 🔐 인증 방식

### HttpOnly Cookie 기반 인증
- **Access Token**: 1시간 유효, 모든 API 요청에 자동 포함
- **Refresh Token**: 14일 유효, 토큰 갱신에만 사용
- **보안**: XSS 공격 방지 (JavaScript 접근 불가)

### 프론트엔드 설정 필수사항
```javascript
// Axios 설정
axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,  // ⭐ Cookie 전송 필수
  headers: { 'Content-Type': 'application/json' }
});

// Fetch API 사용 시
fetch('http://localhost:8080/api/...', {
  credentials: 'include'  // ⭐ Cookie 전송 필수
});
```

---

## 📌 API 엔드포인트

### 1. 인증 API

#### POST `/signup`
회원가입

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "독자A",
  "role": "READER"  // 또는 "AUTHOR"
}
```

**Response:**
```json
"회원가입 성공"
```

---

#### POST `/login`
로그인 (HttpOnly Cookie로 토큰 발급)

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
"로그인 성공"
```

**Set-Cookie Headers:**
- `accessToken`: JWT Access Token (HttpOnly, 1시간)
- `refreshToken`: JWT Refresh Token (HttpOnly, 14일)

---

#### GET `/users/me`
내 정보 조회 (인증 필요)

**Response:**
```json
{
  "id": 123,
  "email": "user@example.com",
  "nickname": "독자A",
  "point": 5000
}
```

---

#### POST `/api/auth/refresh`
Access Token 갱신

**Cookie:** `refreshToken` (자동 전송)

**Response:**
```json
"토큰 갱신 성공"
```

---

#### POST `/api/auth/logout`
로그아웃 (현재 기기만)

**Cookie:** `refreshToken` (자동 전송)

**Response:**
```json
"로그아웃 성공"
```

---

#### POST `/api/auth/logout-all`
모든 기기에서 로그아웃 (인증 필요)

**Response:**
```json
"모든 기기에서 로그아웃되었습니다"
```

---

### 2. 소설 API

#### GET `/novels`
소설 목록 조회 (최신순 10개)

**Response:**
```json
[
  {
    "id": 1,
    "title": "운명의 만남",
    "author": "작가A",
    "description": "소설 설명...",
    "viewCount": 15000,
    "authorId": 10,
    "reviews": [...]
  }
]
```

---

#### GET `/novels/search`
소설 검색 (페이징)

**Query Parameters:**
- `title`: 제목 검색어 (선택)
- `author`: 작가명 검색어 (선택)
- `page`: 페이지 번호 (기본: 0)
- `size`: 페이지 크기 (기본: 10)
- `sort`: 정렬 기준 (기본: id,desc)

**Response:**
```json
{
  "content": [...],
  "pageable": {...},
  "totalPages": 10,
  "totalElements": 100
}
```

---

#### GET `/novels/{id}`
소설 상세 조회

**Response:**
```json
{
  "id": 1,
  "title": "운명의 만남",
  "author": "작가A",
  "description": "소설 설명...",
  "viewCount": 15000,
  "authorId": 10,
  "reviews": [
    {
      "id": 1,
      "content": "재미있어요!",
      "rating": 5
    }
  ]
}
```

---

### 3. 에피소드 API

#### GET `/novels/{novelId}/episodes`
에피소드 목록 조회 (인증 선택)

**Response:**
```json
[
  {
    "id": 1,
    "title": "1화 - 시작",
    "content": "에피소드 내용...",
    "price": 100,
    "isFree": false,
    "isPurchased": true,  // 로그인 시: 구매 여부, 미로그인: false
    "viewCount": 1200,
    "purchaseCount": 450
  }
]
```

---

#### GET `/episodes/{id}`
에피소드 읽기 (조회수 자동 증가, 인증 선택)

**Response:**
```json
{
  "id": 1,
  "title": "1화 - 시작",
  "content": "에피소드 전체 내용...",
  "price": 100,
  "isFree": false,
  "viewCount": 1201,  // 조회수 +1 증가됨
  "purchaseCount": 450,
  "novel": {...}
}
```

**주의사항:**
- 24시간 내 동일 사용자 재방문 시 조회수 증가 안 함 (Redis 기반)
- 로그인 시: 사용자 ID 기준
- 미로그인 시: "guest" 기준

---

#### POST `/novels/{novelId}/episodes`
에피소드 생성 (작가만, 인증 필요)

**Request Body:**
```json
{
  "title": "10화 - 클라이막스",
  "content": "에피소드 내용...",
  "isFree": false,
  "price": 150
}
```

**Response:**
```json
"에피소드가 등록되었습니다."
```

---

#### POST `/episodes/{episodeId}/purchase`
에피소드 구매 (인증 필요)

**Response:**
```json
"에피소드를 구매했습니다."
```

---

### 4. 댓글 API

#### GET `/episodes/{episodeId}/comments`
댓글 목록 조회

**Response:**
```json
[
  {
    "id": 1,
    "content": "재미있어요!",
    "writerName": "독자A",
    "likeCount": 10,
    "dislikeCount": 2,
    "isMine": true
  }
]
```

---

#### POST `/episodes/{episodeId}/comments`
댓글 작성 (인증 필요)

**Request Body:**
```json
{
  "content": "정말 재미있네요!"
}
```

**Response:**
```json
{
  "id": 123,
  "content": "정말 재미있네요!",
  ...
}
```

---

#### POST `/comments/{commentId}/like`
댓글 좋아요/싫어요 (인증 필요)

**Query Parameters:**
- `type`: `LIKE` 또는 `DISLIKE`

**Response:**
```json
{
  "id": 1,
  "likeCount": 11,
  "dislikeCount": 2
}
```

---

### 5. 대시보드 API

#### GET `/api/dashboard/author/{authorId}`
작가 대시보드 조회

**Query Parameters:**
- `date`: 조회 날짜 (기본: 오늘, 형식: YYYY-MM-DD)
- `periodType`: 기간 타입 (DAILY, WEEKLY, MONTHLY, 기본: DAILY)

**Response:**
```json
{
  "authorId": 10,
  "novelId": null,
  "aggregatedDate": "2025-12-06",
  "periodType": "DAILY",
  "totalViews": 15000,
  "uniqueReaders": 8500,
  "avgReadingTimeMinutes": 7,
  "completionRate": 67.5,
  "totalReviews": 234,
  "positiveRate": 72.5,
  "negativeRate": 15.0,
  "insightScore": 78.5,
  "topEpisodes": "[{...}]",
  "dropOffEpisodes": "[{...}]"
}
```

---

#### GET `/api/dashboard/author/{authorId}/trend`
트렌드 조회 (차트용)

**Query Parameters:**
- `limit`: 조회 개수 (기본: 30)
- `periodType`: 기간 타입 (기본: DAILY)

**Response:**
```json
[
  {
    "aggregatedDate": "2025-12-01",
    "totalViews": 1200,
    "uniqueReaders": 800,
    "completionRate": 65.0,
    ...
  },
  {
    "aggregatedDate": "2025-12-02",
    "totalViews": 1500,
    "uniqueReaders": 900,
    "completionRate": 68.0,
    ...
  }
]
```

---

#### GET `/api/dashboard/novels/{novelId}/episodes/best`
베스트 에피소드 TOP N

**Query Parameters:**
- `date`: 조회 날짜 (기본: 오늘)
- `limit`: 조회 개수 (기본: 10)

**Response:**
```json
[
  {
    "episodeId": 10,
    "completionRate": 92.0,
    "totalReaders": 5200,
    "completedReaders": 4784,
    "avgScrollDepth": 95.5
  }
]
```

---

#### GET `/api/dashboard/novels/{novelId}/episodes/worst`
워스트 에피소드 TOP N

**Query Parameters:**
- `date`: 조회 날짜 (기본: 오늘)
- `limit`: 조회 개수 (기본: 10)

**Response:**
```json
[
  {
    "episodeId": 7,
    "completionRate": 42.0,
    "totalReaders": 3200,
    "completedReaders": 1344,
    "avgScrollDepth": 55.2
  }
]
```

---

#### GET `/api/dashboard/episodes/{episodeId}/drop-off`
에피소드 이탈 지점 상세 분석

**Query Parameters:**
- `date`: 조회 날짜 (기본: 오늘)

**Response:**
```json
{
  "episodeId": 7,
  "dropOffMap": {
    "0-10%": 50,
    "10-20%": 120,
    "20-30%": 200,
    "30-40%": 180,
    "40-50%": 100,
    ...
  },
  "highestDropOffRange": "20-30%",
  "recommendation": "에피소드 중반부를 검토하세요. 전개가 지루하거나 불필요한 설명이 많을 수 있습니다."
}
```

---

#### GET `/api/dashboard/episodes/{episodeId}/current-readers`
현재 읽는 사용자 수 (실시간)

**Response:**
```json
{
  "episodeId": 789,
  "currentReaders": 42
}
```

---

#### GET `/api/dashboard/episodes/{episodeId}/views`
에피소드 누적 조회수 (실시간)

**Response:**
```json
{
  "episodeId": 789,
  "totalViews": 15234
}
```

---

### 6. 결제 API

#### POST `/episodes/{episodeId}/purchase`
에피소드 구매 (인증 필요)

**Response:**
```json
"에피소드를 구매했습니다."
```

---

#### GET `/users/me/history`
내 포인트 사용 내역 (인증 필요)

**Response:**
```json
[
  {
    "id": 1,
    "userId": 123,
    "amount": -100,
    "description": "에피소드 구매: 1화",
    "createdAt": "2025-12-06T10:30:00"
  }
]
```

---

## 🔒 인증이 필요한 API

### 필수 인증
- `/users/me` - 내 정보
- `/api/auth/logout-all` - 전체 로그아웃
- `/novels/{novelId}/episodes` (POST) - 에피소드 생성
- `/episodes/{episodeId}/purchase` - 에피소드 구매
- `/episodes/{episodeId}/comments` (POST) - 댓글 작성
- `/comments/{commentId}/like` (POST) - 좋아요/싫어요
- `/users/me/history` - 포인트 내역

### 선택 인증 (로그인 시 추가 정보 제공)
- `/novels/{novelId}/episodes` (GET) - 구매 여부 확인
- `/episodes/{id}` (GET) - 개인별 조회수 카운팅

---

## ⚠️ 에러 응답

### 통일된 에러 형식
```json
{
  "message": "사용자 친화적인 메시지",
  "errorCode": "ERROR_CODE",
  "timestamp": "2025-12-06T10:30:45",
  "path": "/api/novels/999"
}
```

### 주요 에러 코드

| HTTP Status | Error Code | 설명 |
|------------|------------|------|
| 400 | INVALID_REQUEST | 잘못된 요청 |
| 401 | UNAUTHORIZED | 인증 필요 |
| 403 | FORBIDDEN | 권한 없음 |
| 404 | MEMBER_NOT_FOUND | 회원을 찾을 수 없음 |
| 404 | NOVEL_NOT_FOUND | 소설을 찾을 수 없음 |
| 404 | EPISODE_NOT_FOUND | 에피소드를 찾을 수 없음 |
| 404 | NOTIFICATION_NOT_FOUND | 알림을 찾을 수 없음 |
| 409 | DUPLICATE_RESOURCE | 중복된 데이터 존재 |
| 500 | INTERNAL_SERVER_ERROR | 서버 오류 |

---

## 📝 프론트엔드 구현 가이드

### 1. Axios 인스턴스 설정

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,  // ⭐ 필수: Cookie 전송
  headers: { 'Content-Type': 'application/json' }
});

// 401 에러 시 자동 토큰 갱신
api.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      // Refresh Token으로 갱신 시도
      try {
        await axios.post('http://localhost:8080/api/auth/refresh', {}, {
          withCredentials: true
        });
        // 원래 요청 재시도
        return api.request(error.config);
      } catch {
        // Refresh Token도 만료 → 로그인 페이지로
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
```

### 2. 로그인 처리

```javascript
// ❌ 잘못된 방법 (localStorage 사용)
const response = await api.post('/login', { email, password });
localStorage.setItem('token', response.data.token);  // 백엔드가 토큰을 반환하지 않음!

// ✅ 올바른 방법 (Cookie 자동 처리)
await api.post('/login', { email, password });
// 백엔드가 Set-Cookie 헤더로 토큰을 설정함
// 이후 모든 요청에 자동으로 Cookie가 포함됨
```

### 3. 로그아웃 처리

```javascript
// 단일 기기 로그아웃
await api.post('/api/auth/logout');

// 모든 기기 로그아웃
await api.post('/api/auth/logout-all');

// 로그인 페이지로 이동
router.push('/login');
```

### 4. 인증 상태 확인

```javascript
// ❌ 잘못된 방법 (localStorage 확인)
const isLoggedIn = !!localStorage.getItem('token');

// ✅ 올바른 방법 (백엔드에 확인)
try {
  const response = await api.get('/users/me');
  // 로그인 상태
  const user = response.data;
} catch {
  // 로그아웃 상태
}
```

---

## 🚀 대시보드 활용 예시

### Chart.js로 트렌드 차트 그리기

```javascript
// 1. 트렌드 데이터 가져오기
const response = await api.get('/api/dashboard/author/10/trend?limit=30');
const trendData = response.data;

// 2. Chart.js로 시각화
new Chart(ctx, {
  type: 'line',
  data: {
    labels: trendData.map(d => d.aggregatedDate),
    datasets: [
      {
        label: '조회수',
        data: trendData.map(d => d.totalViews),
        borderColor: '#667eea',
        yAxisID: 'y'
      },
      {
        label: '완독률 (%)',
        data: trendData.map(d => d.completionRate),
        borderColor: '#f59e0b',
        yAxisID: 'y1'
      }
    ]
  },
  options: {
    responsive: true,
    scales: {
      y: { position: 'left' },
      y1: { position: 'right', grid: { drawOnChartArea: false } }
    }
  }
});
```

---

## 📊 데이터 수집 흐름

### 에피소드 읽기 로그

1. **사용자가 에피소드 읽기**: `GET /episodes/{id}`
2. **백엔드 자동 처리**:
   - 조회수 증가 (24시간 내 중복 방지)
   - Redis에 사용자 기록
3. **배치 작업** (매일 자정):
   - 로그 집계
   - 대시보드 캐시 생성
4. **대시보드 조회**: `/api/dashboard/author/{authorId}`

### 읽기 시간 및 진도율 추적

프론트엔드에서 별도 구현 필요:

```javascript
// 에피소드 읽기 시작
const startTime = Date.now();
let maxScrollDepth = 0;

// 스크롤 추적
window.addEventListener('scroll', () => {
  const scrollDepth = (window.scrollY / document.body.scrollHeight) * 100;
  maxScrollDepth = Math.max(maxScrollDepth, scrollDepth);
});

// 에피소드 읽기 종료 (페이지 이탈 시)
window.addEventListener('beforeunload', async () => {
  const readingTime = Math.floor((Date.now() - startTime) / 1000);

  // 백엔드에 로그 전송 (비동기, 별도 구현 필요)
  await api.post('/api/logs/reading', {
    episodeId: 123,
    readingTimeSeconds: readingTime,
    scrollDepth: maxScrollDepth,
    completed: maxScrollDepth >= 90
  });
});
```

---

## 🎯 다음 단계: 프론트엔드 구현

1. ✅ Axios 설정 수정 (`withCredentials: true`)
2. ✅ 인증 로직 Cookie 기반으로 변경
3. ✅ 대시보드 Vue 컴포넌트 생성
4. ✅ 읽기 로그 추적 기능 구현
5. ✅ UI/UX 개선 (Tailwind CSS)

