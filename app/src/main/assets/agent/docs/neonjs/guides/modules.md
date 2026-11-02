---
title: 모듈 나누기
description: import와 export로 스크립트를 여러 파일로 나누고, 여러 프로젝트가 함께 쓰는 모듈과 npm 라이브러리를 불러와요.
order: 1
---

NeonJS 프로젝트의 파일은 모두 **ES 모듈**이에요. 다른 파일의 코드는 `import`로 불러오고, 내보낼 값은 `export`로 표시해요.

```javascript title="utils/format.js"
export function won(amount) {
    return `${String(amount).replace(/\B(?=(\d{3})+(?!\d))/g, ",")}원`;
}

export const DEFAULT_PREFIX = "/";
```

```javascript title="main.js"
import { won, DEFAULT_PREFIX } from "./utils/format.js";

export function onMessage(event) {
    if (event.message === DEFAULT_PREFIX + "가격")
        event.room.send(won(15000));   // 15,000원
}
```

> [!NOTE]
> `require()`와 `module.exports`(CommonJS)는 쓸 수 없어요. Rhino 프로젝트의 모듈을 가져올 때는 `export`로 바꿔 주세요.

## 모듈을 찾는 순서

| 적는 방법 | 찾는 곳 |
|---|---|
| `./`, `../`로 시작 (`"./utils/format.js"`) | 지금 파일이 있는 폴더를 기준으로 찾아요. |
| 그 밖의 이름 (`"utils/format"`, `"dayjs"`) | 1. 프로젝트 폴더 → 2. `StarLight/modules` 폴더 순으로 찾아요. |

각 위치에서는 아래 순서로 파일을 찾아요. 그래서 `.js`나 `/index.js`는 생략할 수 있어요.

1. 적은 이름 그대로 (`format`)
2. 뒤에 `.js`를 붙인 파일 (`format.js`)
3. 그 이름의 폴더 안 `index.js` (`format/index.js`)

모듈을 찾지 못하면 컴파일할 때 `Cannot find module '<이름>'` 오류가 나요.

## 여러 프로젝트가 함께 쓰는 모듈

여러 프로젝트에서 쓰는 코드는 `StarLight/modules` 폴더에 두면 프로젝트마다 복사하지 않아도 돼요. 이 폴더는 설정에서 켜야 모듈 경로에 들어가요.

1. **설정 → 프로젝트, 플러그인**에서 **전역 모듈 로드**를 켜요.
2. `StarLight/modules/<이름>.js` 또는 `StarLight/modules/<이름>/index.js`에 모듈을 둬요.
3. 프로젝트에서 `import { … } from "<이름>"`으로 불러와요.

같은 이름이 프로젝트 폴더에도 있으면 프로젝트 폴더의 것이 먼저예요.

> [!WARNING]
> 전역 모듈은 모든 프로젝트의 코드에 섞여 실행돼요. 출처를 확인한 모듈만 넣으세요.

## JSON과 텍스트 파일

가져오기 속성(import attributes)으로 JSON이나 텍스트 파일을 바로 불러올 수 있어요.

```javascript
import config from "./config.json" with { type: "json" };
import help from "./help.txt" with { type: "text" };
```

> [!NOTE]
> 파일은 UTF-8 텍스트로 읽혀요. 이미지 같은 바이너리 파일은 `import`하지 말고 `FileStream`이나 Java API로 읽으세요.

## 동적 import

`import()`는 필요할 때 모듈을 불러와요. 결과는 Promise예요.

```javascript
export async function onMessage(event) {
    if (event.message === "/운세") {
        const { draw } = await import("./fortune.js");
        event.room.send(draw());
    }
}
```

## 모듈이 다시 읽히는 때

모듈은 프로젝트를 **컴파일할 때** 읽혀요. 모듈 파일을 고친 뒤에는 그 모듈을 쓰는 프로젝트를 다시 컴파일해야 바뀐 내용이 반영돼요. 프로젝트마다 실행 환경이 따로라서, 같은 모듈이라도 프로젝트끼리 상태(모듈 안의 변수)를 공유하지 않아요.

## 관련 설정

프로젝트 설정의 **NeonJS** 카테고리에서 모듈 불러오기를 조정할 수 있어요. 자세한 내용은 [언어 설정](/docs/neonjs/reference/settings)을 보세요.

| 설정 | 기본값 | 설명 |
|---|---|---|
| **외부 모듈 로드** | 켜짐 | 끄면 다른 파일을 `import`할 수 없어요. |
| **샌드박스 환경에서 로드** | 켜짐 | 프로젝트 폴더와 `StarLight/modules` 밖의 파일(예: `../다른프로젝트/a.js`)은 불러오지 않아요. |

## npm 라이브러리 쓰기

NeonJS는 `node_modules`를 읽지 않아요. npm 패키지를 쓰려면 PC에서 [esbuild](https://esbuild.github.io/) 같은 번들러로 **ES 모듈 파일 하나**로 묶은 뒤 프로젝트 폴더나 `StarLight/modules`에 넣으세요.

```sh
npm install dayjs esbuild
npx esbuild node_modules/dayjs/esm/index.js --bundle --format=esm --platform=neutral --outfile=dayjs.js
```

```javascript title="main.js"
import dayjs from "./dayjs.js";

console.log(dayjs().format("YYYY-MM-DD"));
```

- `--format=esm`으로 묶어야 `import`로 불러올 수 있어요.
- Node.js API(`fs`, `path`, `Buffer`, `process` 등)에 기대는 패키지는 동작하지 않아요. 브라우저에서도 돌아가는 패키지를 고르세요.
- 네트워크가 필요한 패키지는 `fetch`를 쓰는 판(브라우저용)을 고르면 대부분 그대로 동작해요.
