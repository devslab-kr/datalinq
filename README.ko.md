# DataLinq

[English](README.md) | **한국어**

TUI 기반 데이터 마이그레이션 도구입니다([TamboUI](https://github.com/tamboui/tamboui)로 제작).
**기본 메뉴는 코드로 고정**되고, **마이그레이션 메뉴는 `sql/` 폴더를 스캔해 자동 발견**됩니다 —
폴더 하나 떨구면 메뉴가 됩니다.

**임의의 두 JDBC 데이터베이스 사이**로 행을 옮깁니다(크로스벤더, 소스 + 타겟). SQL Server,
MariaDB/MySQL, PostgreSQL 드라이버는 **번들**되어 있고, 그 외(Oracle, H2, SQLite, ...)는 `driver`
명령 한 줄로 내려받습니다. 연결은 **DB 종류 + host/port/database**(또는 raw URL)로 설정하며,
`application.yml` 에 적거나 DB 연결 화면에서 바로 편집·저장할 수 있습니다.

> **DevsLab Co., Ltd.** (주식회사 데브스랩) 제작 · https://devslab.kr · Apache-2.0

## 빠른 시작

아무것도 없는 상태에서 첫 마이그레이션까지 다섯 단계. 모든 블록은 복붙하면 됩니다.

### 1. 설치

```bash
jbang app install datalinq@devslab-kr/datalinq   # `datalinq` 명령 생성 (jbang이 JDK도 준비해 줌)
```

> jbang이 없나요? [최신 릴리스](https://github.com/devslab-kr/datalinq/releases/latest)에서 `datalinq.jar` 를 받아, 이 가이드의 `datalinq` 자리에 `java -jar datalinq.jar` 를 쓰면 됩니다. **JDK 21+** 필요.

### 2. 둘러보기 — DB 없이도 됨

```bash
datalinq init     # application.example.yml, i18n/, branding/, 샘플 sql/ 폴더를 여기에 생성
datalinq list     # sql/ 아래에서 발견된 마이그레이션 목록
datalinq          # TUI 실행 (방향키 / 숫자키로 이동, q 또는 Esc로 종료)
```

### 3. 데이터베이스 연결

`init` 이 `application.example.yml` 을 만들어 줍니다. 복사해서 소스 하나, 타겟 하나를 채우세요:

```bash
cp application.example.yml application.yml
```

```yaml
datasources:
  my-source:
    type: sqlserver          # sqlserver | mariadb | postgresql   (또는: type: custom + raw url:)
    host: localhost
    port: 1433
    database: SourceDb
    username: sa
    password: "secret"
  my-target:
    type: postgresql
    host: localhost
    port: 5432
    database: TargetDb
    username: postgres
    password: "secret"
defaults:
  source: my-source
  target: my-target
```

```bash
datalinq config   # 해석된 설정 확인 (비밀번호는 마스킹)
```

> 번들되지 않은 드라이버(Oracle, H2, SQLite, ...)가 필요하면 `datalinq driver oracle` 후 그 데이터소스를 `type: custom` + JDBC `url:` 로 설정하세요.

### 4. 첫 마이그레이션 작성

마이그레이션은 `sql/` 아래 폴더 하나입니다. 가장 단순한 **ETL** 은 소스 쿼리의 행을 타겟 테이블로 복사합니다. SELECT의 **컬럼 별칭이 곧 타겟 컬럼**이 되므로 INSERT문을 직접 쓰지 않습니다:

```bash
mkdir -p sql/01_Customers
```

`sql/01_Customers/source.sql`:

```sql
SELECT customer_id AS id,
       full_name   AS name,
       created_at  AS created
FROM   customers
```

`sql/01_Customers/operation.properties`:

```properties
type=etl
table=customers       # INSERT 대상 타겟 테이블
```

### 5. 실행 — 항상 dry-run 먼저

```bash
datalinq run 0             # DRY-RUN: 소스를 읽지만 아무것도 쓰지 않음 (타겟 트랜잭션 롤백)
datalinq run 0 --execute   # 실제 실행: 단일 트랜잭션, 성공 시 커밋 / 오류 시 롤백
```

또는 TUI에서: `datalinq` 실행 → 마이그레이션 선택 → Enter. `sql/` 아래에 `NN_이름` 폴더를 더 떨구면 각각 메뉴 항목이 됩니다.

<details>
<summary><b>👉 데이터가 실제로 옮겨지는 걸 보고 싶다면? Docker로 완전 복붙 데모 — 내 DB가 없어도 됩니다.</b></summary>

throwaway PostgreSQL 두 개를 띄우고, 소스를 채우고, 마이그레이션을 실행해 복사된 행을 출력합니다. (end-to-end 검증 완료)

```bash
# 1. throwaway Postgres 두 개 (소스 5433, 타겟 5434)
docker run -d --name dl-src -p 5433:5432 -e POSTGRES_PASSWORD=demo postgres:16-alpine
docker run -d --name dl-tgt -p 5434:5432 -e POSTGRES_PASSWORD=demo postgres:16-alpine
sleep 5

# 2. 소스 채우기 + 빈 타겟 테이블 생성
docker exec dl-src psql -U postgres -c "CREATE TABLE customers(customer_id int, full_name text, created_at timestamp); INSERT INTO customers VALUES (1,'Alice',now()),(2,'Bob',now());"
docker exec dl-tgt psql -U postgres -c "CREATE TABLE customers(id int primary key, name text, created timestamp);"

# 3. 설정 + 마이그레이션 하나가 든 작업 폴더
mkdir -p dl-demo/sql/01_Customers && cd dl-demo
cat > application.yml <<'YAML'
datasources:
  my-source:
    type: postgresql
    host: localhost
    port: 5433
    database: postgres
    username: postgres
    password: demo
  my-target:
    type: postgresql
    host: localhost
    port: 5434
    database: postgres
    username: postgres
    password: demo
defaults:
  source: my-source
  target: my-target
YAML
cat > sql/01_Customers/source.sql <<'SQL'
SELECT customer_id AS id, full_name AS name, created_at AS created FROM customers
SQL
printf 'type=etl\ntable=customers\n' > sql/01_Customers/operation.properties

# 4. 실제 실행 후 타겟 확인
datalinq run 0 --execute
docker exec dl-tgt psql -U postgres -c "SELECT * FROM customers ORDER BY id;"   # -> Alice, Bob

# 5. 정리
cd .. && docker rm -f dl-src dl-tgt
```

</details>

## 마이그레이션 추가 = 폴더 떨구기

```
sql/
├── 01_Approval_Lines/       # ETL: source.sql(별칭 SELECT) -> 타겟 테이블
│   ├── source.sql
│   └── operation.properties     ->  type=etl, table=approval_lines
├── 03_Reset_Base_Data/      # SCRIPT: .sql 을 타겟에 실행(초기화/삭제)
│   ├── 01_reset.sql
│   └── operation.properties     ->  type=script, destructive=true
└── 05_Orders_With_Items/    # HANDLER: 커스텀 코드(마스터/디테일 분리)
    ├── source.sql
    └── operation.properties     ->  handler=orders
```

- 폴더 `NN_Some_Name` -> 순서 `NN`, 메뉴 라벨 `Some Name`.
- `operation.properties` 는 선택사항입니다. `source.sql` 이 있으면 기본 ETL, 없으면 SCRIPT.

## 세 가지 작업 유형

| 유형 | 폴더 구성 | 하는 일 | 코드? |
|------|-----------|---------|-------|
| **etl** | `source.sql` + `table=<table>` | 소스를 읽어 타겟에 자동 INSERT. SELECT의 **컬럼 별칭 = 타겟 컬럼**이라 INSERT문을 따로 쓰지 않음. | 없음 |
| **script** | `.sql` 하나 이상 | **타겟**에 대해 실행(초기화/삭제). | 없음 |
| **handler** | `source.sql` + `handler=<name>` | `MigrationHandler`(변환 / 마스터-디테일 / 생성키 FK), ServiceLoader로 발견. | ~20줄 |

### Handler (하나의 결과셋이 여러 테이블이 될 때)

```java
public class OrdersMigration extends MigrationHandler {
    public String name() { return "orders"; }     // handler=orders 로 참조
    public void migrate() throws Exception {
        var rows = query("source.sql");            // 평면 결과셋
        // order_no 로 묶어 마스터(orders) insert -> 생성된 id 받아 디테일 insert
        long orderId = insert("orders", values("order_no", ..., "customer_id", ...));
        insert("order_items", values("order_id", orderId, "product_id", ..., "qty", ...));
    }
}
```

Handler는 문자열 리플렉션이 아니라 **ServiceLoader**로 해석됩니다
(`META-INF/services/kr.devslab.datalinq.engine.MigrationHandler` 에 등록) — 그래서 GraalVM
네이티브 이미지 가능성이 열려 있습니다.

## 설정 (`application.yml`)

여러 개의 **이름 있는 데이터소스** — 어느 것이든 소스나 타겟이 될 수 있습니다. 작업은 이름으로
고르고, 없으면 `defaults` 로 폴백합니다:

```yaml
datasources:
  # 구조화 타입은 host/port/database 로 JDBC URL을 만듭니다:
  legacy-erp:                      # 소스
    type: sqlserver                # sqlserver | mariadb | postgresql
    host: SRC_HOST
    port: 1433
    database: SRC_DB
    username: sa
    password: ""
  new-core:                        # 타겟 (mariadb 드라이버는 MySQL 서버에도 접속)
    type: mariadb
    host: TGT_HOST
    port: 3306
    database: TGT_DB
    username: root
    password: ""
  # custom 타입은 직접 쓴 URL을 그대로 사용(내려받은 드라이버에 사용):
  # warehouse:
  #   type: custom
  #   url: jdbc:oracle:thin:@HOST:1521:ORCL
  #   username: app
  #   password: ""
defaults:
  source: legacy-erp               # 작업이 source= 를 지정하지 않을 때 사용
  target: new-core                 # 작업이 target= 을 지정하지 않을 때 사용
options:
  batch-size: 1000
  dry-run-default: true
  language: en                     # en | ko  (빈 값 = 시스템 로캘)
  max-parallel: 4                  # 동시 실행 작업 수(DB 연결 수를 제한)
  mask-password: true              # DB 연결 화면: 비밀번호 마스킹(false = 평문 표시)
  # sql-dir: /path/to/sql          # 외부 마이그레이션 폴더(빈 값 = ./sql)
```

작업은 operation.properties의 `source=<name>` / `target=<name>` 으로 실행마다 재정의할 수 있습니다.
`application.example.yml` 을 `application.yml`(gitignore됨)로 복사하세요. 앱 안(DB 연결 화면)에서
편집·저장할 수도 있습니다.

## 데이터베이스 드라이버

- **번들**(항상 동작): SQL Server, MariaDB / MySQL, PostgreSQL.
- **다운로드** — Maven Central에서 `~/.datalinq/drivers/` 로 받아 다음 실행 시 로드:

  ```bash
  datalinq driver               # 받을 수 있는 목록
  datalinq driver oracle        # 하나 받기 (oracle | mysql | h2 | sqlite | postgresql)
  ```

  해당 폴더에 JDBC 드라이버 `.jar` 을 손으로 떨궈도 됩니다. 외부에서 로드된 드라이버는
  `DriverShim` 을 통해 등록됩니다(그렇지 않으면 JDK의 `DriverManager` 가 외부 클래스로더의
  드라이버를 무시함). **custom** 타입 + 직접 쓴 URL 로 사용하세요.

## 안전장치

- **기본이 dry-run** — 타겟 트랜잭션을 롤백하므로 아무것도 쓰지 않습니다.
- `destructive=true` 작업은 실행 전 명시적 확인을 요구합니다.
- 각 작업은 **하나의 타겟 트랜잭션** 안에서 실행: 성공 시 커밋, 오류 시 롤백.

## 설치 & 실행

기본 명령은 **TUI** 이고, 모든 하위 명령은 스크립트화할 수 있습니다.

### jbang으로 (권장)

[jbang](https://www.jbang.dev/) 은 DataLinq를 실제 `datalinq` 명령으로 설치해 주고, JDK가 없으면
알맞은 JDK까지 받아 줍니다:

```bash
jbang app install datalinq@devslab-kr/datalinq   # 한 번만 - `datalinq` 명령 생성
datalinq                                          # TUI 실행 (마이그레이션 / DB 연결 / 설정 / 정보)
datalinq init                                     # 편집용 기본값 생성 (i18n/, branding/, 예시 설정, sql/)
datalinq list                                     # 발견된 작업 목록
datalinq run 0                                    # 0번 작업 dry-run
datalinq run 0 --execute                          # 실제 쓰기
```

설치 없이 한 번만 실행하려면: `jbang datalinq@devslab-kr/datalinq`.

### jar로 (jbang 없이)

[최신 릴리스](https://github.com/devslab-kr/datalinq/releases/latest)에서 `datalinq.jar` 를 받아
직접 실행합니다(**JDK 21+** 필요). `java -jar datalinq.jar <명령>` 은 `datalinq <명령>` 과 동일합니다:

```bash
java -jar datalinq.jar              # TUI
java -jar datalinq.jar config       # 해석된 설정 표시(비밀번호 마스킹)
java -jar datalinq.jar run 0 --execute
```

개발 중에는 `./gradlew run --args="..."` 도 동작하며, `./gradlew shadowJar` 로 jar을 빌드합니다.

## 상태

엔진(스캐너 / ETL / script / handler / 트랜잭션 / dry-run), `application.yml` 설정,
번들 + 다운로드 JDBC 드라이버, CLI, 그리고 **TamboUI TUI**(마이그레이션, DB 연결, 설정, 정보 —
모두 동일한 `MigrationEngine` 호출)가 구현·검증되었습니다.
