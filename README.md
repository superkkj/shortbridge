# ShortBridge 🎬

> 짧은 영상 1개를 올리면, **유튜브 Shorts / 인스타 Reels / 틱톡 / X(트위터)** 4곳에 한 번에 올려주는 도구예요.

---

## 🧒 한 줄로 설명

크리에이터가 같은 짧은 영상을 매번 **4개 앱에 따로따로** 올리느라 귀찮잖아요?
ShortBridge는 그걸 **한 번만** 하면 알아서 4곳에 다 올려줘요.

비유:
- 여러분이 편지 한 통을 4명의 친구에게 보내고 싶어요.
- 우체국(ShortBridge)에 편지를 한 번 맡기고 친구 4명 이름을 적어주면,
- 우체부(Worker)가 알아서 4명에게 각각 보내줘요.
- 어떤 친구는 받았고, 어떤 친구는 주소가 틀려서 못 받았다고 **결과도 알려줘요**.

---

## 🗺️ ShortBridge 안에 뭐가 들어 있어요?

```
[브라우저]  ←  사람이 보는 화면 (Thymeleaf + Bootstrap)
   ↕
[Spring Boot 앱]  ←  진짜 일하는 두뇌
   ↕         ↕              ↕
[Postgres] [RabbitMQ]    [MinIO/S3]
 데이터     심부름꾼대기실   영상 창고
```

- **Postgres**: 회원·영상·게시물 정보를 적어두는 **노트**
- **MinIO**: 영상 파일을 진짜로 저장하는 **창고** (운영에서는 AWS S3)
- **RabbitMQ**: "이거 유튜브에 올려" 같은 **심부름 쪽지**를 모아두는 곳
- **Worker**: 쪽지를 보고 실제로 유튜브/틱톡 같은 데에 영상을 올려주는 **심부름꾼**

---

## 🚦 처음 시작하기 (5분 가이드)

### 0. 미리 준비할 거
- macOS 또는 리눅스
- **Docker Desktop** 설치 (https://www.docker.com/products/docker-desktop)
- **Java 21** (Gradle이 자동 다운로드해줘서 안 깔려 있어도 됨)

### 1. 컨테이너 켜기 (Postgres + RabbitMQ + MinIO)

```bash
cd /Users/apple/Desktop/shortbridge/docker
docker compose up -d
```

이러면 백그라운드에서 노트(Postgres) + 심부름쪽지(RabbitMQ) + 창고(MinIO) 3개가 켜져요.

확인:
```bash
docker compose ps
```
3개 다 `(healthy)` 라고 뜨면 OK.

### 2. OAuth 계정 정보 채우기

`src/main/resources/application-local.yml.example` 을 복사해서 `application-local.yml` 로 만들고, 비어있는 **client-id / client-secret** 을 채워줘요.

처음에는 **Google 로그인** 하나만 채우면 돼요:
- https://console.cloud.google.com/apis/credentials 가서 "OAuth 클라이언트 ID" 만들기
- 종류: **웹 애플리케이션**
- 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`
- 발급받은 ID/Secret을 yml에 붙여넣기

### 3. 앱 켜기

```bash
cd /Users/apple/Desktop/shortbridge
gradle bootRun --args="--spring.profiles.active=local"
```

5초쯤 기다리면 `Started ShortBridgeApplication in 4.8 seconds` 라고 뜸.

### 4. 브라우저로 접속

http://localhost:8080 → "Google 로 시작하기" 버튼 클릭 → Google 계정으로 로그인 → 대시보드 🎉

---

## 📱 화면 구경

| 화면 | URL | 무엇을 해요? |
|---|---|---|
| 로그인 | `/login` | Google/인스타/X로 들어가기 |
| 대시보드 | `/dashboard` | 내 영상·게시물·연결된 플랫폼 한눈에 |
| 플랫폼 연결 | `/social-accounts` | 유튜브/인스타/틱톡/X 계정 연결하기 |
| 영상 목록 | `/videos` | 올린 영상들 |
| 새 영상 | `/videos/new` | 영상 파일 업로드 |
| 게시물 목록 | `/posts` | 만든 게시물 + 발행 상태 |
| 새 게시물 | `/posts/new` | 영상 고르고 제목 쓰고 어디 올릴지 선택 |
| 게시물 상세 | `/posts/{id}` | 플랫폼별로 어떻게 됐는지, 실패하면 재시도 |

---

## 🎯 진짜 한 번 써보기 (실제 흐름)

```
1. /login      → Google로 로그인 🔑
2. /social-accounts → 유튜브 "연결" 클릭 → 권한 허용 ✅
3. /videos/new      → MP4 영상 1개 선택 → 업로드 ⬆️
4. /posts/new       → 영상 선택 + 제목/설명 입력 + 유튜브 체크 + "만들기" 🎬
5. /posts/{id}      → 유튜브 PUBLISHED 됐는지 확인 🟢
```

영상 1개 = 게시물 여러 개 가능합니다 (같은 영상을 다른 제목으로 또 올릴 수 있어요).

---

## 📂 영상 규칙

| 항목 | 제한 |
|---|---|
| 포맷 | **MP4** (h264 video + aac audio) |
| 길이 | **140초** 이하 (4개 플랫폼 공통 최대값) |
| 크기 | **512MB** 이하 |
| 화면 | 세로형(9:16) 또는 정사각형(1:1) 권장 (가로형도 업로드는 됨) |

규칙 어기면 업로드 시점에 빨간 메시지로 알려줘요. (예: "영상 길이가 제한을 초과했습니다: 200")

---

## 🔌 플랫폼 연결 정책

| 플랫폼 | 신용카드 | 사전 준비 | 게시 제한 |
|---|---|---|---|
| **유튜브** | 불필요 | Google Cloud에서 YouTube Data API v3 활성화 | 미검증 앱은 **비공개 업로드만** |
| **틱톡** | 불필요 | https://developers.tiktok.com 가입 + 앱 등록 + `video.publish` scope | audit 전 **비공개만** |
| **인스타** | 불필요 | Meta App + **Professional 계정** + App Review | 심사 전 테스트 계정만 |
| **X(트위터)** | **필요** ⚠️ | X Developer Portal 등록 + 결제 | pay-per-usage. 기본 OFF |

처음에는 **유튜브부터** 추천드려요. 가장 빠르고 무료.

---

## ❓ 자주 묻는 질문 (FAQ)

### Q1. 영상을 올렸는데 왜 유튜브에 자동으로 안 가요?
A. 영상 업로드와 게시물 발행은 **다른 단계**예요.
1. `/videos/new` 에서 영상 올림 (창고에 보관만)
2. `/posts/new` 에서 그 영상을 골라 "유튜브로 발행" 버튼 눌러야 진짜 보내짐
같은 영상을 여러 번 다른 제목으로 발행할 수 있게 일부러 분리했어요.

### Q2. "플랫폼 연결" 안 했는데 게시물 만들 수 있어요?
A. 아니요. 게시할 곳이 1개라도 연결되어 있어야 만들기 버튼이 활성화됩니다. `/social-accounts` 먼저 가세요.

### Q3. 유튜브에 올렸는데 다른 사람한테 안 보여요.
A. 미검증 OAuth 앱에서 올린 영상은 **무조건 비공개(private)** 입니다. 본인만 볼 수 있어요. 공개로 풀려면 Google YouTube API Services Audit 신청해서 통과해야 해요 (개인 개발자가 받기는 어려움).

### Q4. 영상이 게시물에서 "FAILED_TEMPORARY"로 떠요.
A. 일시적 실패예요. 토큰 만료, 네트워크 오류, 일시 API 오류 등. 게시물 상세 화면에서 **재시도** 버튼 클릭하면 다시 시도해요.

### Q5. 게시물이 "RECONNECT_REQUIRED" 상태예요.
A. 그 플랫폼의 access token이 만료됐고 refresh도 안 됐어요. `/social-accounts` 에서 그 플랫폼 "연결 해제" → 다시 "연결" 누르세요.

### Q6. 영상 파일은 어디 저장돼요?
A. 로컬에서는 `storage-local/videos/2026/05/18/UUID.mp4` 형식. 운영에서는 S3.

### Q7. 비밀번호 같은 거 어디 적어요?
A. **절대 yml 에 직접 쓰지 마세요.** `application-local.yml` 은 git ignore 됐고, 운영은 환경변수로만:
```bash
export GOOGLE_CLIENT_SECRET=...
export TOKEN_KEY_V1=...
```

### Q8. 로그아웃하면 다 지워져요?
A. 아니요. Google 계정 정보(`users`, `login_accounts`)와 영상, 게시물은 그대로. 다음에 다시 같은 Google 계정으로 로그인하면 그대로 나와요.

---

## 🛠️ 자주 쓰는 명령

```bash
# 컨테이너 시작 / 중지
cd docker
docker compose up -d
docker compose down

# 앱 켜기
gradle bootRun --args="--spring.profiles.active=local"

# 컴파일만
gradle compileJava

# 로그 보기 (앱이 백그라운드일 때)
tail -f /tmp/shortbridge.log

# DB 직접 보기
docker exec -it shortbridge-postgres psql -U shortbridge -d shortbridge

# RabbitMQ 관리 화면
open http://localhost:15672    # 계정: shortbridge / shortbridge

# MinIO 콘솔 (영상 창고)
open http://localhost:9901     # 계정: minioadmin / minioadmin
```

---

## 🧩 폴더 구조 (개발자용)

```
src/main/java/com/shortbridge/
├── ShortBridgeApplication.java
├── common/          ← 모두 함께 쓰는 도구함 (Base 엔티티, 예외, 저장소)
├── support/         ← 보안/큐/암호화 같은 보조 기능
├── platform/        ← 진짜 비즈니스 데이터 (user/video/post/...)
│   ├── user/
│   ├── loginaccount/
│   ├── socialaccount/
│   ├── video/
│   ├── post/
│   ├── posttarget/
│   └── publishjob/
├── connect/         ← 플랫폼 OAuth 연결 (유튜브/틱톡 전략 패턴)
└── publish/         ← 진짜 발행 일하는 곳
    ├── publisher/   ← 공통 interface
    ├── youtube/
    ├── instagram/
    ├── tiktok/
    └── twitter/
```

규칙:
- **Controller**는 얇게 (요청 받고 Facade 호출만)
- **Facade**가 트랜잭션 + 여러 Service 조합 + Entity → Response 변환
- **Service**는 도메인 단위 (Command/Query 분리)
- **JPA Relation(@ManyToOne) 사용 금지** — `UUID userId` 같이 ID만 보관

---

## 🧪 테스트해보기

```bash
# 컴파일 + 단위 테스트 (Testcontainers 필요 없음)
gradle test

# 그냥 빌드 (테스트 제외)
gradle build -x test
```

---

## 🆘 도움말

- 설계 의도: `/Users/apple/Documents/project-docs/shortbridge/docs/shortform_multi_upload_design_v0.3.md`
- 개발 규칙: `DEVELOPER_GUIDE_LITE.md`
- 문제 생기면: 로그 (`/tmp/shortbridge.log`) 보고, ERROR 줄 위주로 확인

---

## 🚧 아직 안 된 것

| 기능 | 상태 |
|---|---|
| Google 로그인 | ✅ 완성 |
| 영상 업로드 + ffprobe 검증 | ✅ 완성 |
| 유튜브/틱톡 연결 (OAuth flow) | ✅ 코드 완성 (사용자가 외부 OAuth 등록만 마치면 됨) |
| 인스타/X 연결 | ⏳ 미구현 (외부 등록 + connector 추가 필요) |
| 4개 플랫폼 실제 발행 API 호출 | 🟡 **stub 상태** (코드 자리만 잡혀 있고 실제 호출은 TODO) |
| 예약 발행 (Scheduled) | ✅ 흐름 완성 (Worker stub 호출까지 작동) |
| 재시도 / DLQ | ✅ 흐름 완성 |
| 화면 (대시보드/게시물/영상/플랫폼) | ✅ 완성 |

**stub** 이라는 건 "흐름은 다 만들어졌고 실제 외부 API 호출만 가짜로 성공 응답을 돌려주는 자리표시" 라는 뜻이에요. 실제로 유튜브에 영상이 올라가지는 않습니다. 외부 API 연동 코드를 채워넣어야 진짜 발행돼요.

---

*Last Updated: 2026-05-18*
