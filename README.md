# Time Servlet з Thymeleaf і Cookie — модуль 11

У цьому ДЗ я взяв застосунок із 10 модуля та додав Thymeleaf і запам'ятовування останнього часового поясу. Працює на Apache Tomcat 9.

## Що зроблено

- `TimeServlet` — обчислює поточний час, передає `currentTime` і `timezone` в Thymeleaf та повертає готову HTML-сторінку.
- `src/main/resources/templates/time.html` — сам HTML-шаблон. Текст часу виводиться через `th:text`, а не збирається рядками у Java.
- `TimezoneValidateFilter` — перевіряє параметр `timezone`. Якщо пояс неправильний, повертає HTTP 400 та `Invalid timezone`.
- `TimezoneCookieService` — зберігає останній валідний пояс у Cookie `lastTimezone` та читає його в наступному запиті.
- `TimezoneResolver` — допоміжний клас для перевірки та перетворення часових поясів.

Логіка вибору поясу така:

1. Якщо в URL є `timezone`, перевіряємо саме його. Валідне значення записуємо в `lastTimezone`.
2. Якщо параметра немає, пробуємо взяти пояс із Cookie.
3. Якщо немає ні параметра, ні правильної Cookie — використовуємо `UTC`.
4. Невалідний **query-параметр** повертає HTTP 400 і **не змінює Cookie**. Некоректну Cookie ігноруємо та використовуємо UTC.

Cookie зберігається на 30 днів, має `HttpOnly` та шлях `/` (коли застосунок розгорнутий як `ROOT.war`). Значення, як-от `UTC+2`, зберігається без втрати знака `+`.

## Технології

- Java 17+ (я працюю з JDK 26.0.1);
- Gradle 9.4.1, плагін `war`;
- `javax.servlet-api:4.0.1`;
- `org.thymeleaf:thymeleaf:3.1.7.RELEASE`;
- Apache Tomcat 9.0.124.

Залишив `javax.servlet` із попереднього модуля, оскільки використовую Tomcat 9. Для Tomcat 10+ знадобився б перехід на `jakarta.servlet`.

## Як зібрати

Після розпакування архіву я спочатку копіюю Gradle Wrapper з 10 модуля. Це потрібно тільки для початкового архіву: після завантаження проєкту на GitHub Wrapper уже буде в репозиторії. Команди для моїх папок:

```powershell
cd "F:\ProjectsTEST\Java-Gradle-Thymeleaf-Time-Cookies"
Copy-Item "..\Java-Gradle-Tomcat-TimeServlet\gradlew" . -Force
Copy-Item "..\Java-Gradle-Tomcat-TimeServlet\gradlew.bat" . -Force
New-Item -ItemType Directory -Force ".\gradle\wrapper" | Out-Null
Copy-Item "..\Java-Gradle-Tomcat-TimeServlet\gradle\wrapper\*" ".\gradle\wrapper\" -Force
```

Потім збираю проєкт у PowerShell:

```powershell
.\gradlew.bat clean check war
```

Збірка створює `build/libs/ROOT.war`. Команда `check` запускає `TimeServletSelfTest`, де перевіряються шаблон, час, Cookie, пріоритет параметра та HTTP 400.

## Запуск через Tomcat

1. Зупинити Tomcat у попередньому вікні PowerShell натисканням `Ctrl+C`.
2. Замінити старий файл `webapps/ROOT.war` новим `build/libs/ROOT.war`. Якщо Tomcat не оновив розпаковану папку `webapps/ROOT`, її можна видалити **після зупинки сервера**; це розпакована копія WAR, а не папка з кодом.
3. Налаштувати `JAVA_HOME`, `CATALINA_HOME` та запустити `catalina.bat run`.

У мене Tomcat лежить у `F:\ProjectsTEST\apache-tomcat-9.0.124`, а порт `8080` зайнятий Steam, тому використовую `8081`. На іншому ПК може бути стандартний порт `8080`.

```powershell
$tomcat = "F:\ProjectsTEST\apache-tomcat-9.0.124"
Copy-Item ".\build\libs\ROOT.war" "$tomcat\webapps\ROOT.war" -Force

$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.1"
$env:JRE_HOME = $env:JAVA_HOME
$env:CATALINA_HOME = $tomcat
$env:CATALINA_BASE = $tomcat
& "$tomcat\bin\catalina.bat" run
```

## Перевірка

Спочатку в браузері без старих Cookie відкриваємо `http://localhost:8081/time` — має бути час UTC.

Потім переходимо за `http://localhost:8081/time?timezone=UTC%2B2` — час UTC+2 і нова Cookie `lastTimezone=UTC+2`.

Після цього відкриваємо звичайний `http://localhost:8081/time` — завдяки Cookie залишається UTC+2.

| Приклад URL | Очікуваний результат |
| --- | --- |
| `/time` (новий браузер без Cookie) | Час UTC, HTTP 200 |
| `/time?timezone=UTC%2B2` | Час UTC+2, нова Cookie, HTTP 200 |
| `/time` (після попереднього запиту) | Час UTC+2 із Cookie, HTTP 200 |
| `/time?timezone=Europe/Kyiv` | Час Києва, Cookie оновлюється |
| `/time?timezone=UTC%2B99` | `Invalid timezone`, HTTP 400, Cookie не змінюється |

Символ `+` у query-параметрі краще записувати як `%2B`, інакше браузер може передати його як пробіл.

Для перевірки HTTP-заголовків у PowerShell можна використати:

```powershell
curl.exe -i "http://localhost:8081/time?timezone=UTC%2B2"
```

У відповіді має бути `Set-Cookie: lastTimezone=UTC+2` (можуть додаватися `Path`, `Max-Age`, `HttpOnly`).

Проєкт містить Java-файли, Thymeleaf-шаблон, Gradle, тести та `web.xml`. У GitHub не додаю папки `.gradle`, `.idea`, `build` і локально зібраний WAR.
