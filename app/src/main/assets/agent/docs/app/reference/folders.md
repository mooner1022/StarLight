---
title: 폴더 구조
description: StarLight가 기기 저장소에 만드는 폴더와 파일, 프로젝트 폴더와 project.json의 내용을 정리했어요.
order: 3
---

StarLight는 내장 저장소의 `StarLight` 폴더(`/storage/emulated/0/StarLight`)에 모든 데이터를 저장해요. 하위 폴더는 처음 필요할 때 만들어져요.

```text
StarLight/
├─ projects/                 프로젝트
│  └─ <이름>/
├─ plugins/                  플러그인
│  ├─ <ID>.slp
│  └─ <이름>(<ID>)/          플러그인 데이터 (config-plugin.json)
├─ modules/                  여러 프로젝트가 함께 쓰는 모듈 (직접 만들어요)
├─ logs/
│  └─ <날짜>/<시각>.log      앱 실행마다 하나씩
├─ config-general.json       앱 설정
└─ notification_rules_v2.json  패키지 규칙
```

| 경로 | 설명 |
|---|---|
| `projects/` | 프로젝트마다 폴더가 하나씩 있어요. 아래에서 설명해요. |
| `plugins/` | 플러그인 파일과 플러그인별 데이터 폴더예요. [플러그인 설치와 관리](/docs/app/guides/plugins)를 보세요. |
| `modules/` | 앱이 만들지 않으니 직접 만들어요. **설정 → 프로젝트, 플러그인 → 전역 모듈 로드**를 켜야 쓰여요. |
| `logs/` | 앱 로그 파일이에요. 보관 기간은 **설정 → 로그**에서 정해요. |
| `config-general.json` | 설정 탭의 값과 홈 위젯 배치예요. |
| `notification_rules_v2.json` | **패키지 규칙 설정**에서 만든 규칙이에요. |
| `STARTUP.info` | 앱이 비정상 종료됐을 때 남는 오류 보고예요. |

## 프로젝트 폴더

```text
projects/hello-bot/
├─ project.json       프로젝트 정보
├─ hello-bot.js       메인 스크립트
├─ config.json        프로젝트 설정 (언어 설정 포함)
├─ logs.json          프로젝트 로그 (최근 50개)
├─ .env               환경 변수 (직접 만들어요)
├─ data/              DataBase API가 쓰는 파일
└─ debugroom/
   └─ chats.json      디버그 룸 대화 기록
```

메인 스크립트 말고도 모듈 파일이나 데이터 파일을 자유롭게 넣을 수 있어요. 에디터의 파일 서랍에서 만들거나, 파일 관리자로 복사해요.

### project.json

| 키 | 설명 |
|---|---|
| `id` | 프로젝트의 고유 ID(UUID)예요. |
| `name` | 프로젝트 이름이에요. 폴더 이름과 같아요. |
| `mainScript` | 메인 스크립트 파일 이름이에요. |
| `languageId` | 언어 ID예요. NeonJS는 `js_neon`, Rhino는 `JS_RHINO`예요. |
| `isEnabled` | 켜져 있는지예요. |
| `isPinned` | 즐겨찾기했는지예요. |
| `createdMillis` | 만든 시각(밀리초)이에요. |
| `allowedEventIds` | 호출이 허용된 이벤트 ID 목록이에요. 기본값은 `["starlight.*"]`이에요. |
| `packages` | 프로젝트 정보 화면에 보이는 패키지 목록이에요. 메시지를 거르는 데 쓰이지는 않아요. |

> [!WARNING]
> `project.json`을 직접 고칠 때는 앱을 끄고 고치거나, 고친 뒤 프로젝트 설정의 **위험 → 프로젝트 정보 다시 로드**를 누르세요. 특히 `languageId`를 바꾸면 프로젝트가 동작하지 않을 수 있어요.

## 백업과 옮기기

- 프로젝트 하나를 옮기려면 `projects/<이름>/` 폴더를 통째로 복사해요.
- 앱 전체를 옮기려면 `StarLight` 폴더를 통째로 복사해요. 새 기기에서 앱을 설치하고 권한을 허용한 뒤 폴더를 넣고, 앱을 다시 시작하세요.
