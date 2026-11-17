# 토스페이먼츠 개발 설정

## 1단계: 서버 테스트 설정

Java 21과 기존 Gradle Wrapper를 사용합니다. 각자 IntelliJ의 KeyboardProjectApplication → Edit Configurations → Environment variables에 개인 값을 설정하세요. 공유 실행 구성·소스·Git에 키나 비밀번호를 저장하지 마세요. .env.example은 변수 이름 참고용이며 Spring Boot가 자동 로드하지 않습니다. .env.local을 작성해도 별도 로더 없이 백엔드 실행 환경에 전달되지 않습니다.

- DB_USERNAME, DB_PASSWORD: 기존 로컬 MySQL 접속 계정. 각자 입력하며 비밀번호를 출력하지 않습니다.
- TOSS_PAYMENTS_SECRET_KEY: 개발자센터 상단 ‘주문서형·결제창형 연동 키’의 테스트 시크릿 키(test_gsk_ 접두어).
- TOSS_PAYMENTS_TEST_ENABLED: 테스트 키 설정을 검사하려면 true. 기본 false이며 꺼져 있으면 키를 요구하지 않습니다.
- SPRING_PROFILES_ACTIVE: 기존 활성 프로필을 보존하세요. 1단계는 별도 local 프로필을 요구하지 않습니다. 3단계 테스트 주문에는 local이 필요합니다.
- TOSS_PAYMENTS_DEV_ORDERS_ENABLED: 기본 false. 3단계 개발 주문을 사용할 때만 true.

프론트 NEXT_PUBLIC_TOSS_PAYMENTS_CLIENT_KEY와 서버 시크릿은 같은 상단 테스트 키 쌍을 사용해야 합니다. 하단 API 개별 연동 키(test_sk_)·클라이언트 키·운영 키는 서버 테스트 설정에서 거부합니다. 키 누락/형식 오류도 명확한 설정 오류로 종료하며 원문을 출력하지 않습니다. 실제 키 및 공식 예제 키 값을 코드·테스트·문서에 넣지 마세요. 테스트의 fixture_only 문자열은 인증에 사용할 수 없는 임의 검증 데이터입니다.

설정 변경 후 해당 프로젝트 서버를 중지하고 위 개인 실행 구성으로 재시작합니다. 이미 8080 포트를 쓰는 프로그램이 있으면 다른 프로그램을 임의 종료하지 마세요.

DB는 localhost:3306/keyboard_shop을 사용합니다. 기존 스키마가 필요하며 ddl-auto=none, spring.sql.init.mode=never로 DB 구조를 변경하지 않습니다. 이 브랜치에는 DB 생성/마이그레이션이나 회원 기능을 포함하지 않습니다. 기존 테이블을 임의 생성하지 마세요.

### 테스트 환경 분리

개인 키 환경변수가 없는 별도 셸에서 Gradle 테스트를 실행하세요. DB_USERNAME/DB_PASSWORD만 필요한 경우 전달하고 TOSS_PAYMENTS_SECRET_KEY, TOSS_PAYMENTS_TEST_ENABLED, TOSS_PAYMENTS_DEV_ORDERS_ENABLED, SPRING_PROFILES_ACTIVE는 테스트 프로세스에서 제거합니다. 개인 실행 구성 자체의 값을 지우라는 뜻이 아닙니다.

- ./gradlew test --tests '*TossPaymentsTestConfigurationTests'
- ./gradlew clean build

키 부재/잘못된 종류/운영 키 거부/비활성화/안전한 표시를 외부 API 호출 없이 검사합니다. 환경변수 로드 성공은 실제 토스 API 인증·결제 성공을 뜻하지 않습니다.

공식 문서: https://docs.tosspayments.com/sdk/v2/js/payment-widget

## 3단계: 개발 전용 테스트 주문

백엔드 실행 환경에 TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true를 추가하고 SPRING_PROFILES_ACTIVE에 local을 포함하세요. 기존 프로필이 있다면 지우지 말고 쉼표로 추가하되 prod 또는 production이 활성화된 환경에서는 이 API를 사용할 수 없습니다. 운영 프로필을 지우거나 운영에서 local을 켜서 테스트하지 마세요. 기본값은 비활성화이며 local과 명시적 true를 모두 요구합니다. 설정 변경 후 해당 서버만 재시작합니다.

- POST /api/dev/toss-orders: 본문/쿼리 없이 요청. 201 ApiResponse로 서버가 생성한 orderId, orderName, amount=10000, currency=KRW, status=PENDING 반환. 입력이 있으면 400.
- GET /api/dev/toss-orders/{orderId}: 저장 주문 조회 200. 잘못된 형식400, 없음/만료404.
- 메모리 저장: ConcurrentHashMap, UUID 및 putIfAbsent, 동기화된 생성으로 중복/한도 보호. 최대1000건,30분 만료. 한도429, 반복 충돌503. 서버 재시작 시 소멸하며 DB Entity·Repository를 사용하지 않습니다.
- 루프백 주소에서만 생성·조회 허용. CORS는 http://localhost:3000만 허용. 해당 생성 POST만 CSRF 검사에서 제외합니다. 기존 다른 경로의 CSRF와 기본 인증 보호는 유지합니다.

이 원격 브랜치는 초기 세팅 기반으로 회원 보안 코드가 없습니다. 회원 기능을 가져오는 대신 개발 주문 활성화 시 최후순위 기본 보호 체인(formLogin/httpBasic)을 포함해 사용자 정의 체인 추가로 다른 경로가 공개되는 것을 방지했습니다. 비활성화 시 기존 Spring Boot 기본 보안이 동작합니다. 향후 팀 공통 인증 설정과 통합할 때 체인 순서·CORS·CSRF를 함께 검토해야 하며 쿠키 인증 구현을 추가한 것은 아닙니다.

프론트에서도 개인 .env.local에 TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true를 설정하고 재시작한 뒤 http://localhost:3000/dev/toss-order에서 확인합니다. 서버 금액 조회 및 SDK 금액 설정 완료까지만 수행합니다.

검증: ./gradlew test --tests '*DevTossOrder*', ./gradlew clean build. 앞 절의 개인 키 없는 테스트 환경을 사용합니다. 테스트는 메모리 저장·동시300건·충돌·만료·한도·입력/조회 오류·루프백/CORS·프로필 차단·기존 기본 보호를 확인합니다.



## 4·5단계: 공식 UI와 결제 요청·구매자 인증

기존 개발 주문과 공식 renderPaymentMethods/renderAgreement UI를 유지하고 테스트 결제하기 버튼을 연결했습니다. UI 렌더 완료 전에는 요청할 수 없습니다. 서버 GET으로 저장된 주문을 결제 직전에 다시 확인하고 주문번호·상품명·KRW 금액·PENDING을 비교합니다. 15초 조회 제한, 즉시 중복 잠금, 요청 중 주문 재생성 차단, 오류·취소 재시도를 처리합니다. SDK 자체가 진행 중일 때 임의 타임아웃으로 잠금을 해제하지 않습니다.

requestPayment에는 SDK가 지원하는 orderId/orderName/successUrl/failUrl을 전달합니다. 금액은 3단계 setAmount의 서버 금액을 사용합니다. getSelectedPaymentMethod는 이번 흐름에 필요하지 않아 호출하지 않습니다. 공식 SDK가 선택된 결제수단과 약관 검사를 처리하며 카드사·앱 인증 UI를 직접 만들지 않았습니다.

결과 경로는 /dev/toss-order/success와 /dev/toss-order/fail입니다. 두 경로도 개발 모드와 명시적 활성화를 요구합니다. 성공 URL은 요청 직전 sessionStorage에 저장한 주문과 orderId/amount/paymentKey 필수값을 검사하고 서버 주문을 재조회합니다. paymentKey는 로그·저장소·화면에 기록하지 않습니다. URL만으로 토스 인증의 진위를 확정할 수 없으며 최종 승인/결제완료 처리는 하지 않습니다. 실패 코드는 제한된 형식만 표시하고 제공자의 원문 메시지는 출력하지 않습니다.

기존 테스트 키 검사(test_gck_)를 요청 전에도 반복합니다. 테스트 주문은 서버 메모리 저장(최대1000건/30분)이며 실제 DB 주문이 아닙니다. 실제 주문으로 교체할 때는 dev-test-orders.ts의 연결과 서버 주문 검증을 함께 교체해야 합니다. /checkout과 회원·로그인 코드는 변경하지 않았습니다.

### 검증과 수동 확인

- pnpm run test:payments-sdk: 기존20 + 신규9 = 29개 통과.
- node node_modules/typescript/bin/tsc --noEmit: 통과.
- Windows에서 기존 POSIX build 스크립트와 동등하게 NODE_OPTIONS=--no-deprecation을 프로세스에 설정하고 node node_modules/next/dist/bin/next build: 통과. 기존 Google Fonts 다운로드를 위한 네트워크 접근이 필요했습니다.
- 백엔드 기존 결제 설정·개발 주문 테스트22개와 Gradle build 통과. 전체 애플리케이션 MySQL 테스트는 실행하지 않았습니다.

실제 인증 확인: 이 작업 공간을 테스트 키 및 TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true로 localhost:3000에 실행하고, 기존 개발 주문 백엔드(local 프로필+활성화)를 사용하세요. 기존 서버를 임의 종료하지 말고 포트 전환은 사용자가 결정해야 합니다. 개인 .env.local/키는 작업 공간에 복사하거나 커밋하지 않았습니다. 테스트 공개 클라이언트 키는 별도 미리보기 프로세스의 환경변수로만 전달했습니다.

1. /dev/toss-order 접속 → 테스트 주문 준비 → 공식 결제수단·약관 표시.
2. 필수 약관 확인 → 테스트 결제하기 → 공식 결제창/구매자 인증에서 사용자 조작.
3. 성공 시 /dev/toss-order/success에서 서버 주문 일치와 최종 승인 미실행 문구 확인.
4. 취소 시 현재 화면 안내 또는 SDK 동작에 따른 실패 URL 이동 확인. 취소가 항상 failUrl로 이동한다고 가정하지 않습니다.
5. 실패 경로, 연속 클릭, 주문 만료 후 요청 거부를 확인합니다.

별도 포트3001 미리보기에서는 SDK 초기화, 주문 준비 오류 안내, fail?code=USER_CANCEL 표시, 필수값 없는 success 거부를 브라우저로 확인했습니다. 기존 백엔드 CORS가 localhost:3000만 허용하므로3001에서 정상 주문 준비·공식 결제 인증까지 검증하지 않았습니다. 실제 카드사/휴대폰/앱 인증, 성공 리다이렉트, 결제수단별 실패 리다이렉트와 실청구 없음은 사용자 확인이 남아 있습니다. 합성 URL 확인은 실제 토스 인증 성공을 의미하지 않습니다.

최종 승인 API, 결제 DB 저장, 취소 API, 웹훅은 이번 단계에 포함하지 않습니다. Git commit/push/merge/PR을 수행하지 않았습니다.


## 6단계: 결과 검증·오류 분류·승인 연결 준비

기존 /dev/toss-order/success 및 /dev/toss-order/fail과 공통 결과 화면을 재사용합니다. /payment/* 신규 경로는 만들지 않습니다.

성공값은 누락/중복/공백/제어문자·paymentKey 길이, 양의 안전 정수 amount, 요청 당시 sessionStorage 주문과의 일치를 검사합니다. 기존 readSuccess 표시 계약은 paymentKey를 반환하지 않으며, validateSuccess에서만 향후 승인 요청용 paymentKey/orderId/amount 자료형을 준비합니다. 실제 승인 커넥터·URL·API 호출은 없습니다. 승인 중/실패 상태 문구는 7번 연결을 위한 예약 상태이며 현재 실행 경로에서 가짜로 표시하지 않습니다.

결과 처리 흐름은 서버 주문 GET을 재사용하고 주문번호/상품명/금액/통화/PENDING을 비교합니다. 검증 실패, 주문 없음/만료, 불일치, 네트워크/시간초과, 서버 오류를 구분합니다. 정상 비교 후에도 승인 API 미연결·승인 미실행·결제 완료 아님을 표시합니다. 결제 완료 상태는 없습니다.

동일 주문의 진행 중인 조회는 공유해 Strict Mode/중복 마운트 요청을 줄입니다. 구독 화면이 사라져도 다른 구독자의 공유 조회를 중단하지 않으며, 화면에는 늦은 결과를 반영하지 않습니다. 조회는15초 제한입니다. 새로고침은 서버 주문을 다시 검증하고 이전 검토 마커를 결제 완료로 신뢰하지 않습니다. 검토 마커에는 주문번호/승인 API 대기 상태만 기록하고 paymentKey는 기록하지 않습니다. 이것은 서버 멱등성 구현이 아닙니다.

실패는 사용자 취소/카드사 거절/일반 실패/시스템 오류로 분류합니다. 외부 message는 선택값이며 길이·중복·제어문자를 검사하고 화면에 원문을 출력하지 않습니다. 취소 결과에는 orderId가 없을 수 있어 선택값으로 처리합니다. 재시도는 기존 개발 결제 화면 링크를 사용합니다.

7번 이후: 실제 승인 URL·요청/응답 계약, 서버 측 멱등성, 승인 결과 재조회·저장·응답 검증, 실제 승인 성공/실패 테스트가 필요합니다. 프론트 잠금만으로 새로고침·다른 탭·다른 장치의 중복 승인을 막을 수는 없습니다. 실제 주문 API/표시는 후속 범위입니다.

이번 단계는 실제 인증·승인 요청, DB 변경, 회원/쇼핑몰 변경, Git commit/push/merge/PR 및 실행 중 서버 전환을 하지 않습니다. 6-1~6-18 원문은 현재 대화·첨부·프로젝트 문서에서 확인되지 않아, 사용자 메시지의14개 요구 항목을 기준으로 구현했습니다. 원문 번호별 완료 판정은 보류합니다.

## 7단계: 개발 주문 결제 승인 API

`POST /api/payments/confirm`은 기존 개발 주문만 사용합니다. 프론트 6단계는 아직 연결하지 않았습니다.
회원·상품 서비스, JWT 쿠키 정책, 공통 SecurityConfig, 실제 주문 DB 및 Payment 엔티티는 변경하지 않습니다.

### 활성화와 접근

- `local` 활성화, `prod`와 `production` 비활성화
- `TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true`
- `TOSS_PAYMENTS_TEST_ENABLED=true`
- `TOSS_PAYMENTS_DEV_CONFIRM_ENABLED=true` (기본 false)
- 기존 `TOSS_PAYMENTS_SECRET_KEY`의 주문서형 테스트 시크릿 키 사용
- 직접 연결한 루프백 주소와 기존 `cors.allowed-origin`과 일치하는 Origin 필요
- POST application/json만 허용. 브라우저가 아닌 호출도 동일 Origin 헤더를 명시해야 함
- X-Forwarded-For로 루프백을 우회할 수 없음. 역방향 프록시/운영 서버용 구성이 아님
- 전용 보안 체인은 비활성 환경에서도 경로를 차단하며 공통 로그인·회원 정책을 변경하지 않음

실제 키나 비밀값을 문서·테스트·로그에 기록하지 않습니다. 개인 환경파일은 수정하지 않습니다.

### 요청과 응답

요청 JSON: `paymentKey`(공백 없는 불투명 문자열, 최대200자), `orderId`(기존 dev_ 주문번호),
`amount`(양의 JSON 정수). 숫자 문자열·소수·범위 초과 값은 거부합니다.
신규 승인 전에 서버 개발 주문의 존재·만료·금액·통화·PENDING 상태를 확인합니다.
잘못된 주문·금액은 토스 HTTP 호출 전에 거부합니다. 기존 주문 API와 일치하게 없음/만료는404입니다.

응답은 기존 `{success,message,data}` 구조입니다. data는 orderId, amount, currency,
paymentStatus, completed, method, approvedAt, storage를 포함합니다. paymentKey나 원본 Payment는 반환하지 않습니다.
`storage=MEMORY_ONLY`이며 실제 DB 저장 성공을 의미하지 않습니다.

- 200 / DONE / completed=true: 승인 응답 검증 완료
- 200 / WAITING_FOR_DEPOSIT / completed=false: 가상계좌 입금 대기, 결제 완료 아님
- 202 / CONFIRMING 또는 UNKNOWN / completed=false: 처리 중 또는 결과 미확정
- 202에는 Retry-After:3이 있으나 클라이언트가 무한 자동 재요청해서는 안 됨
- 400: 요청 오류, 403: 개발 접근 조건 위반, 404: 주문 없음/만료
- 409: 금액/요청/결제상태 충돌, 422: 제공자의 확정 거절, 429: 메모리 한도
- 502: 제공자 인증/키 설정 오류
- 오류 data.code는 내부 코드이며 data.providerCode에는 허용 목록의 공식 코드만 포함

모든 승인 응답은 Cache-Control:no-store입니다. JSON 이외의 요청은 보안 단계에서403입니다.
`success=true`만 보고 결제 완료로 표시하지 말고 completed와 paymentStatus를 함께 확인해야 합니다.

### 외부 호출과 안전한 재조회

- POST https://api.tosspayments.com/v1/payments/confirm
- Basic 인증은 시크릿 뒤 콜론을 붙여 인코딩하며 로그에 출력하지 않음
- 외부 요청 금액은 서버 주문과 비교한 값만 사용
- 호스트 고정, 리다이렉트 미허용, 연결5초·응답65초(설정 범위60~120초)
- 주문별 고정 Idempotency-Key를 사용. 오류가 났다는 이유로 새 키를 만들지 않음
- 타임아웃, 5XX, 408/409/429, 이미 처리된 결제, 비정상 응답은 UNKNOWN
- 같은 UNKNOWN 요청은 GET /v1/payments/{paymentKey}로만 재조회하며 승인 POST를 반복하지 않음
- 조회404/실패/미승인 상태는 실패 확정으로 보지 않고 UNKNOWN 유지. 자동 재승인 없음
- 주문번호 조회 API는 현재 test_gsk 키의 지원 범위가 아니므로 사용하지 않음
- 응답의 결제키·주문번호·금액·KRW·상태와 완료 시 승인일시·수단을 검증

### 메모리 한계와 후속 작업

주문당 하나의 요청을 원자적으로 확보하며 같은 주문의 다른 키·금액, 다른 주문의 동일 키 재사용을409로 거부합니다.
동일 완료 요청은 캐시 결과를 반환합니다. 외부 호출 중에는 전체 저장소 잠금을 잡지 않습니다.
승인 기록은 최대1000건이며 미확정/완료 기록을 자동 삭제하지 않습니다. 한도 도달 시 신규 승인은429입니다.
기존 개발 주문이 만료돼도 이미 시작한 승인은 고정된 주문정보로 재조회할 수 있습니다.
재시작 시 주문/승인 메모리는 사라집니다. 재시작 후 원본 주문이 없으면 외부 승인을 새로 보내지 않습니다.
다중 서버·재시작 복구·영속 멱등성은 지원하지 않으며 운영 결제에 사용할 수 없습니다.

7-16의 전달 자료는 검증된 응답 DTO로 준비했습니다. 8단계 DB 저장 연결과 실제 주문 소유자 검증은 후속입니다.
7-23의 실제 샌드박스 승인은 별도 구매자 인증 후 확인해야 합니다. 자동화 테스트는 가짜 HTTP 응답만 사용합니다.
프론트 연결 시 기존15초 조회 제한을 승인 POST에 재사용하지 말고 서버 승인 대기보다 긴 제한을 사용하세요.
화면 취소/언마운트/네트워크 실패를 결제 실패로 단정하지 말고 같은 요청을 통해 조회해야 합니다.

공식 근거:
- https://docs.tosspayments.com/reference
- https://docs.tosspayments.com/reference/using-api/authorization
- https://docs.tosspayments.com/reference/using-api/api-keys
