---
title: Java와 Android API 쓰기
description: Java.type으로 Java·Android 클래스를 불러오고, 객체와 컬렉션, 콜백, 예외를 JS에서 다뤄요.
order: 2
---

NeonJS 프로젝트는 `Java` 전역 객체로 Java와 Android의 클래스에 접근해요. StarLight는 NeonJS에 **모든 호스트 접근을 허용**해서, 스크립트는 기기에서 앱이 할 수 있는 거의 모든 일을 할 수 있어요. Google Play 판에는 [몇 가지 제한](#google-play-판의-제한)이 있어요.

## 클래스 불러오기: Java.type

`Java.type("전체 클래스 이름")`은 클래스를 돌려줘요. 돌려받은 값으로 객체를 만들고, 정적 멤버를 부르고, `instanceof`로 검사해요.

```javascript title="main.js"
const ArrayList = Java.type("java.util.ArrayList");
const JMath = Java.type("java.lang.Math");   // JS의 Math와 이름이 겹치지 않게

const list = new ArrayList();
list.add("별");
list.add("빛");

console.log(list.size());       // 2
console.log(JMath.max(3, 7));   // 7
console.log(list instanceof ArrayList);  // true
```

- 중첩 클래스는 바깥 클래스의 정적 멤버로 쓰거나 `$`로 이어 적어요. `Java.type("android.os.Build").VERSION.SDK_INT`와 `Java.type("android.os.Build$VERSION").SDK_INT`는 같아요.
- 배열 형식은 `Java.type("int[]")`, `Java.type("java.lang.String[]")`처럼 적어요.
- 같은 이름은 언제나 같은 클래스 객체예요(`Java.type("X") === Java.type("X")`).

> [!NOTE]
> Rhino의 `Packages.java.util.ArrayList`, `java.lang.Math`, `importClass()`, `importPackage()`는 NeonJS에 없어요. 모두 `Java.type()`으로 바꿔 주세요.

## 멤버 읽고 쓰기

- **필드**는 이름 그대로 읽고 써요.
- **getter**는 속성처럼 읽을 수 있어요. `getName()`은 `obj.name`, `isEnabled()`는 `obj.enabled`로 읽혀요(Kotlin 속성도 같아요). 같은 이름의 필드나 메서드가 있으면 그쪽이 먼저예요.
- `is`로 시작하는 getter를 `obj.isEnabled`처럼 **메서드 이름 그대로** 쓰면 값이 아니라 함수가 돌아와요. 조건문에서 항상 참이 되니 `obj.isEnabled()`로 부르거나 `obj.enabled`로 읽으세요. `event.room.isGroupChat()`이 대표적인 예예요.
- **메서드 오버로드**는 인자에 가장 잘 맞는 것이 골라져요. 직접 고르려면 시그니처를 이름처럼 적어요.

```javascript
const sb = new (Java.type("java.lang.StringBuilder"))();
sb["append(java.lang.String)"]("hello");
console.log(Java.type("java.lang.Math")["max(int,int)"](1, 2));
```

- 같은 Java 객체는 JS로 넘어올 때마다 같은 값이에요(`a === b`). 내용이 같은 다른 객체는 같지 않아요.
- 필드에 형식이 맞지 않는 값을 넣으면 `TypeError`가 나요(`int` 필드에 `"abc"`).

## 값이 바뀌는 규칙

| Java | JS |
|---|---|
| `int`, `double` 같은 숫자, `String`, `char` | 숫자와 문자열 원시값 |
| `long` | 2⁵³ 이하면 숫자, 그보다 크면 **BigInt** |
| `BigInteger` | BigInt |
| `null` | `null` |
| 그 밖의 객체 (`StringBuilder` 같은 `CharSequence` 포함) | Java 객체 그대로 |

반대로 JS 값을 Java 메서드에 넘길 때는 메서드의 매개변수 형식에 맞춰 바뀌어요. 숫자는 `int`/`long`/`double`로, JS 배열과 객체는 `List`/`Map`으로, JS 함수는 콜백 인터페이스로 바뀌어요.

> [!WARNING]
> **64비트 ID는 BigInt일 수 있어요.** 카카오톡의 `chatLogId`처럼 2⁵³보다 큰 `long`은 BigInt로 들어와요. 이런 값은 숫자 리터럴과 비교하지 말고 BigInt 리터럴(`id === 1234567890123456789n`)이나 문자열(`String(id)`)로 비교하세요. BigInt는 `JSON.stringify()`에 넣으면 오류가 나고, 숫자와 섞어 계산할 수 없어요.

> [!TIP]
> `StringBuilder`나 Android의 `Spanned`처럼 문자열이 아닌 `CharSequence`는 Java 객체로 남아요. 문자열이 필요하면 `String(x)`로 바꾸세요.

## 배열과 컬렉션

Java 배열과 `List`는 JS 배열처럼 인덱스와 `length`로 읽고, `for…of`와 전개 구문(`...`)에도 쓸 수 있어요. `Map`은 항목을 속성처럼 읽고 써요.

```javascript
const HashMap = Java.type("java.util.HashMap");
const map = new HashMap();
map.put("count", 1);
console.log(map.count);          // 1

for (const item of list) console.log(item);
```

JS 배열과 서로 바꿀 때는 `Java.from()`과 `Java.to()`를 써요.

```javascript
const jsArray = Java.from(list);                 // Java 배열·컬렉션 → JS 배열
const longs = Java.to([1, 2, 3], "long[]");      // JS 배열 → Java 배열
```

## 콜백과 인터페이스 구현

JS 함수는 `Runnable`, `Consumer` 같은 단일 메서드 인터페이스나 Kotlin 함수 형식이 필요한 곳에 그대로 넘길 수 있어요. 여러 메서드를 가진 인터페이스에는 JS 객체를 넘겨요.

```javascript
const Thread = Java.type("java.lang.Thread");
new Thread(() => console.log("다른 스레드")).start();
```

Java 클래스를 상속하거나 인터페이스를 구현한 클래스가 필요하면 `Java.extend()`를 써요. 부모 클래스의 원래 메서드는 `Java.super(인스턴스)`로 불러요.

```javascript
const TimerTask = Java.type("java.util.TimerTask");
const Task = Java.extend(TimerTask, {
    run() {
        console.log("tick");
    }
});
new (Java.type("java.util.Timer"))().schedule(new Task(), 1000);
```

| 그 밖의 `Java` 함수 | 설명 |
|---|---|
| `Java.isJavaObject(value)` | Java 객체인지 알려 줘요. |
| `Java.isType(value)` | `Java.type()`이 돌려준 클래스인지 알려 줘요. |

## 예외 처리

Java 메서드가 던진 예외는 JS에서 `name`이 `"HostError"`인 오류로 잡혀요. `message`에는 Java 예외의 메시지가 들어 있어요.

```javascript
try {
    Java.type("java.lang.Integer").parseInt("별");
} catch (e) {
    console.log(e.name);     // HostError
    console.log(e.message);  // For input string: "별"
}
```

## Android API

Android 클래스도 똑같이 `Java.type()`으로 불러요. 앱의 `Context`가 필요한 API는 StarLight의 기본 API(`App`, `Device` 등)를 먼저 찾아보세요.

```javascript title="main.js"
const Build = Java.type("android.os.Build");
console.log(`${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}`);
```

> [!IMPORTANT]
> 스크립트는 프로젝트의 작업 스레드에서 실행돼요. 화면(View)을 직접 바꾸는 API는 메인 스레드에서 불러야 하니 `Handler(Looper.getMainLooper())`로 넘기세요. 짧은 안내 메시지는 StarLight가 제공하는 [`toast()`](/docs/neonjs/reference/globals#starlight가-더하는-전역)가 알아서 메인 스레드로 보내요.

> [!CAUTION]
> NeonJS의 Java 접근에는 차단 목록이 없어요. `System`, `Runtime`, 리플렉션까지 모두 쓸 수 있으니, 출처를 모르는 스크립트나 모듈은 실행하지 마세요.

## Google Play 판의 제한

Google Play 판에서는 앱 밖의 코드를 불러오거나 실행할 수 있는 Java API를 스크립트에서 쓸 수 없어요. NeonJS와 Rhino 프로젝트 모두 같아요.

| 막힌 것 | 예 |
|---|---|
| 리플렉션 | `java.lang.reflect`, `java.lang.invoke`, `Class.forName()`, `getMethod()` 같은 `Class`의 리플렉션 메서드 |
| 클래스와 코드 로딩 | `ClassLoader`, `dalvik.system`의 클래스 |
| 프로세스와 시스템 호출 | `Runtime`, `ProcessBuilder`, `android.system.Os` |
| 앱 설치 | `PackageInstaller` |
| 스크립트 엔진 내부 | `org.mozilla.javascript`(Rhino), `dev.mooner.neonjs`(NeonJS) |

이 밖의 Java와 Android API는 GitHub 판과 똑같이 쓸 수 있어요.
