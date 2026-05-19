# 회고 #001 — ShortBridge 1차 구축 + E2E 자동 발행 검증

**날짜**: 2026-05-18
**작업 범위**: 설계 문서 2건 → 프로젝트 골격 → OAuth 로그인 → 영상 업로드 → 자동 발행 (stub) → 화면 검증
**최종 상태**: ShortBridge **내부 흐름** E2E 통과 (DB 상태 PUBLISHED 3건 + BLOCKED_BY_CAPABILITY 1건). **외부 플랫폼 실제 발행은 0건** (Publisher 4개 모두 stub).

⚠️ **stub vs real 구분 (중요)**: 화면에 "PUBLISHED" 초록 배지가 떠도 그건 ShortBridge DB 상태일 뿐, 진짜 YouTube/Instagram/TikTok/X 에는 영상 안 올라감. Publisher 코드의 TODO 부분을 채워야 진짜 발행 시작.

---

## 1. 만든 것 (검증된 사실)

### 1.1 인프라
| 항목 | 상태 |
|---|---|
| Spring Boot 3.4.1 + Java 21 + Gradle 9.2 골격 | ✅ build SUCCESSFUL, bootJar 76MB |
| PostgreSQL 16 + RabbitMQ 3.13 + MinIO (docker-compose) | ✅ 모두 healthy |
| Flyway V1 (9 테이블 + 인덱스) + V2 (platform_capabilities seed) | ✅ migration 성공 |

### 1.2 도메인 (전부 DEVELOPER_GUIDE_LITE 규약 준수)
| 도메인 | 구조 | 검증 |
|---|---|---|
| user / login_account | Entity + Repo + Query/CommandService + Facade | DB row INSERT 확인 |
| social_account | Token AES-GCM 암호화 | SEED INSERT 4건 |
| video | ffprobe 메타데이터 검증 + S3/Local Storage | 29MB MP4 업로드 + storage-local 파일 저장 확인 |
| post + post_target | 13개 상태 머신 + idempotency_key + 조건부 lock UPDATE | post 1건 + targets 4건 INSERT |
| publish_job | attempt 추적 + status (QUEUED/PROCESSING/SUCCEEDED/FAILED) | jobs 4건, 3 SUCCEEDED + 1 FAILED |

### 1.3 전략 패턴 적용
| 영역 | 인터페이스 | 구현체 |
|---|---|---|
| 발행 (publish) | `SocialPublisher` + `PublisherRegistry` | YouTube / Instagram / TikTok / Twitter (stub) |
| 연결 (connect) | `SocialConnector` + `ConnectorRegistry` | YouTube / TikTok (real OAuth code), Instagram / X (코드 미작성) |

### 1.4 통과한 E2E
1. Google OIDC 로그인 → `users` + `login_accounts` INSERT
2. 영상 multipart 업로드 → ffprobe 검증 → MinIO/Local 저장 → `videos` INSERT
3. 영상 업로드 = 자동 발행 (`/api/v1/videos/quick-publish`) → `posts` + `post_targets` × 4 + RabbitMQ enqueue + Worker 처리 → 화면 표시까지

### 1.5 문서
- `README.md` 초보자 친화 버전 (FAQ 8개, 비유 사용)
- `docs/RETROSPECTIVE-001.md` (본 문서)

---

## 2. 막힌 곳 + 시정 (5건)

### 2.1 OIDC user service 미등록 → NPE on /dashboard
**증상**: Google 로그인 callback 성공 후 `/dashboard` 진입 시
`Cannot invoke "CurrentUser.userId()" because "currentUser" is null`.

**진단** (3-점 증거):
- DashboardViewController.java:22 NPE
- SecurityConfig에 `userInfoEndpoint.userService(oAuth2UserService)` 만 등록
- Google은 OIDC provider → Spring 기본 `OidcUserService` 호출 → `DefaultOidcUser` principal 들어옴 → `CurrentUser` 타입 매치 실패 → resolver null 반환

**fix**: `ShortBridgeOidcUserService` 신규 작성. SecurityConfig에 `.oidcUserService()` 등록.
`CurrentUserArgumentResolver` 에 OAuth2User principal → attributes.userId fallback 추가.

**배운 점**: Spring Security OAuth2와 OIDC는 **분리된 인터페이스**. provider 종류에 따라 등록 위치 다름.

---

### 2.2 PostgreSQL jsonb 컬럼 ↔ JPA String 충돌
**증상**: login_accounts INSERT 시
`column "raw_profile_json" is of type jsonb but expression is of type character varying`.

**진단**: JPA가 String 필드를 jdbc `setString()` 으로 보냄 → PostgreSQL VARCHAR → jsonb implicit cast 안 함.

**fix**: jdbc URL 에 `?stringtype=unspecified` 추가. PG가 알아서 cast 시도.

**배운 점**: PostgreSQL jsonb 컬럼 사용 시 stringtype 옵션 또는 `@JdbcTypeCode(SqlTypes.JSON)` 필요.

---

### 2.3 Thymeleaf layout fragment 미정의
**증상**: `/dashboard` 진입 시 500
`An error happened during template parsing ... dashboard/index.html`

**진단**: `~{layout/base :: html(content=~{::main})}` 호출했으나 `base.html`에 `th:fragment` 정의 없음.

**fix**: `base.html`에 `th:fragment="layout(title, content)"` 추가. 7개 view 페이지를 `<html th:replace="~{layout/base :: layout(title=..., content=~{::section})}">` 패턴으로 통일.

**배운 점**: Thymeleaf fragment 호출 = fragment 정의 양쪽 합의 필수. layout-dialect 미사용 시 root `<html>` 자체를 replace 하는 게 일반적 패턴.

---

### 2.4 PublishWorker self-invocation으로 @Transactional 미적용
**증상**: 게시물 발행 시 `post_targets` LOCKED 까지만 변경되고 `publish_jobs` QUEUED 그대로. 메시지는 ack됨 (큐/DLQ 모두 0건).

**진단** (3-점 증거):
- RabbitPublishGateway enqueued 로그 4건 (메시지 발행 사실)
- RabbitMQ 큐 + DLQ 0건 (메시지 소비 + ack 사실)
- post_targets LOCKED + publish_jobs QUEUED (`tryLockForPublish`는 자체 빈이라 트랜잭션 적용 / `process()`는 같은 객체 self-invocation → Spring AOP 미적용 → dirty checking 작동 안 함)

**fix**: `process()` 메서드를 별도 빈 `PublishProcessor` 로 분리. `PublishWorker.handleXxx` 가 `processor.process(message)` 호출 (다른 빈 호출 → AOP 정상 적용).

**배운 점**: Spring `@Transactional`은 프록시 기반. 같은 클래스 메서드 호출 시 프록시 우회 → AOP 미적용. **자기 자신 메서드 호출하지 말 것**. 별도 빈 분리 또는 self-injection.

---

### 2.5 자동화 도구 lock 충돌
**증상**: chrome-devtools-mcp + Playwright 둘 다 `Browser is already in use` 에러.

**fix**: 기존 MCP 프로세스 종료 후 Playwright만 사용.

**배운 점**: MCP 도구 간 user-data-dir 공유 X. 한 번에 하나만 활성화.

---

## 3. 의도된 stub / 미구현 (다음 작업)

| 항목 | 현재 상태 | 다음 단계 |
|---|---|---|
| 4개 Publisher 실제 API 호출 | stub (가짜 외부 ID 반환) | 각 플랫폼 SDK 또는 HTTP 직접 호출 코드 채우기 |
| Instagram Connector | 코드 미작성 | Meta App 등록 + connector 추가 |
| X(Twitter) Connector | 코드 미작성 | X Developer Portal + 신용카드 + connector 추가 |
| YouTube/TikTok 실제 연결 | 코드 완성, 외부 OAuth 등록 대기 | YouTube Data API v3 활성화 + TikTok App client_key/secret 수령 |
| `BLOCKED_BY_CAPABILITY` `isTerminal()` 처리 | false 반환 | true로 변경하거나 별도 처리 (post status가 PROCESSING 으로 남음) |

---

## 4. 디자인 의도와의 차이 (의식적 변경)

### 4.1 영상 업로드 = 자동 발행
**디자인 문서 17장**: video 업로드 / 게시물 작성을 **별도 단계**로 분리.

**실제 사용자 요구 (2026-05-18)**: "업로드하면 연결된 플랫폼 다 자동으로 올라가야 돼".

**적용**: 디자인 문서 분리 의도(같은 영상 → 여러 게시물 가능)는 backend 그대로 유지. 추가로 `/api/v1/videos/quick-publish` 엔드포인트 신설 — 영상 업로드 직후 PostFacade.create(연결된 모든 플랫폼) 자동 호출. UX 우선.

**남은 약점 U1**: quick-publish 시 제목/설명/해시태그 세밀 입력 어려움. 추후 detail 화면에서 편집 가능하도록 PATCH API 보완 필요.

---

## 5. 자체 감사 (CLAUDE.md F-Rule 4-B 준수)

| 항목 | 인과 발화 prefix | 증거 |
|---|---|---|
| 2.1 NPE | `사실: A → B` (SecurityConfig.userInfoEndpoint 등록 누락 → DefaultOidcUser principal → resolver null 반환 → NPE) | 코드 호출 경로 grep + 스택트레이스 |
| 2.2 jsonb | `사실: A → B` (jdbc setString → PG implicit cast 없음 → SQLGrammarException) | PG 에러 메시지 직접 인용 |
| 2.4 @Transactional | `사실: A → B` (self-invocation → AOP 프록시 우회 → dirty check 미작동) | 코드 호출 경로 (PublishWorker.java:42 → process) + DB 상태 (LOCKED 만 commit) |

반사실 검증: 2.4의 경우 — process()를 별도 빈으로 분리한 후 발행 흐름이 PUBLISHED까지 가는지 검증. **결과: post_targets 3건 PUBLISHED + publish_jobs 3건 SUCCEEDED 확인.** A 제거 시 B 사라짐 검증 완료.

---

## 6. 남은 약점 (후속 패치 필요)

- **U1**: quick-publish 시 제목/설명 세밀 입력 어려움 (4.1 참조)
- **U2**: `BLOCKED_BY_CAPABILITY` 가 `isTerminal() = false` → post.status 가 PROCESSING 으로 영원히 남을 수 있음
- **U3**: 첫 게시물(`e8a9dc62-...`)은 self-invocation 버그 시기에 만들어져 LOCKED 상태로 묶임. 수동 정리 필요
- **U4**: Test 코드 placeholder 1건만 있음 (실제 통합 테스트 미작성)
- **U5**: Instagram/X Connector 미구현
- **U6**: 4개 Publisher 모두 stub (실제 외부 API 호출 미구현)
- **U7**: 운영 환경 시크릿 관리 (현재 yml에 client-secret 평문 박혀 있음 — 운영 배포 전 환경변수 분리 필수)
- **U8** (2026-05-18 사용자 지적 적재): 직전 응답에서 "4건 중 3건 PUBLISHED" 라고만 표기하고 **stub vs real 구분 강조 부족**. 사용자가 진짜 외부 발행으로 오해. 시정: 회고 문서 상단에 ⚠️ 박스 추가 + 표현 가이드 — 발행 관련 메시지에는 **"진짜 외부 호출 0건"** / **"DB 상태만 변경"** 문구 의무 첨부.

검토 시점 (2026-05-18) 에 발견된 위 약점 8건. 실사용 시 추가 발견 시 즉시 [보완 6] 4단계 워크플로우로 적재.

---

## 7. 기록할 시간/노력 단위

| 단계 | 소요 | 주된 작업 |
|---|---|---|
| 설계 문서 정독 + task 등록 | 짧음 | DEVELOPER_GUIDE_LITE + design v0.3 |
| 프로젝트 골격 + 도메인 7개 + publish 4개 + 화면 | 가장 큼 | 120 파일 작성 |
| 1차 빌드 통과 | 짧음 | gradle 9 + Spring Boot 3.4 호환성 조정 + foojay toolchain |
| Google OAuth → 회원가입 E2E | 중간 | OIDC fix + jsonb fix + layout fragment fix |
| 영상 업로드 E2E | 짧음 | 1회 통과 |
| 자동 발행 E2E | 중간 | self-invocation @Transactional 버그 fix + 재검증 |
| 회고 문서 (본 문서) | 짧음 | - |

---

## 8. 다음에 같은 작업 다시 한다면

1. **OIDC vs OAuth2 user service 차이를 처음부터 명시**. SecurityConfig 작성 시 두 가지 다 등록 골격 잡고 시작.
2. **PostgreSQL jsonb 컬럼 사용 entity 작성 시 `stringtype=unspecified` 또는 `@JdbcTypeCode(SqlTypes.JSON)` 둘 중 하나 즉시 적용**.
3. **Thymeleaf layout fragment 패턴을 첫 base.html 작성 시점에 결정**. 매 view 페이지 작성 전에 fragment 호출 형식 확정.
4. **@RabbitListener / @Scheduled 메서드는 처음부터 별도 Processor 빈으로 분리**. self-invocation 함정 회피.
5. **stub publisher 흐름을 social_account SEED INSERT + quick-publish 패턴으로 빠르게 E2E 검증**. 외부 OAuth 등록 전에 흐름 검증 먼저.

---

*Last Updated: 2026-05-18*
