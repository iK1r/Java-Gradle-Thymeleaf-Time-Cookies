package com.example.timeservlet;

import javax.servlet.FilterChain;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeServletSelfTest {
    private static final Pattern TIME = Pattern.compile("<time[^>]*>([^<]+)</time>");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        TimeServlet servlet = new TimeServlet();
        servlet.init();
        TimezoneValidateFilter filter = new TimezoneValidateFilter();

        MockResponse first = run(filter, servlet, null, null);
        checkTime(first, "UTC", ZoneId.of("UTC"));
        require(first.cookies.isEmpty(), "No cookie should be saved without explicit timezone");
        System.out.println("Default UTC: OK");

        MockResponse second = run(filter, servlet, "UTC+2", null);
        checkTime(second, "UTC+2", ZoneId.of("+02:00"));
        require(second.cookies.size() == 1, "Valid query must create one Cookie");
        Cookie saved = second.cookies.get(0);
        require("lastTimezone".equals(saved.getName()), "Wrong Cookie name");
        require("UTC+2".equals(saved.getValue()), "Wrong saved timezone");
        require("/".equals(saved.getPath()), "Cookie must work at /time");

        MockResponse third = run(filter, servlet, null, new Cookie[]{saved});
        checkTime(third, "UTC+2", ZoneId.of("+02:00"));
        require(third.cookies.isEmpty(), "Reading a Cookie should not rewrite it");
        System.out.println("Cookie lastTimezone persistence: OK");

        MockResponse replaced = run(filter, servlet, "Europe/Kyiv", new Cookie[]{saved});
        checkTime(replaced, "Europe/Kyiv", ZoneId.of("Europe/Kyiv"));
        require("Europe/Kyiv".equals(replaced.cookies.get(0).getValue()), "New query must replace saved zone");

        MockResponse encodedPlus = run(filter, servlet, "UTC 2", null);
        checkTime(encodedPlus, "UTC+2", ZoneId.of("+02:00"));
        require("UTC+2".equals(encodedPlus.cookies.get(0).getValue()), "Decoded plus must be normalized");
        System.out.println("Other timezones and plus decoding: OK");

        checkInvalid(filter, servlet, "", new Cookie[]{saved});
        checkInvalid(filter, servlet, "UTC+99", new Cookie[]{saved});
        checkInvalid(filter, servlet, "Mars/Olympus", null);
        checkInvalid(filter, servlet, "UTC+18:01", null);
        System.out.println("Invalid query HTTP 400: OK");

        MockResponse tampered = run(filter, servlet, null,
                new Cookie[]{new Cookie("lastTimezone", "Mars/Olympus")});
        checkTime(tampered, "UTC", ZoneId.of("UTC"));
        System.out.println("Invalid saved Cookie fallback: OK");
        System.out.println("All Thymeleaf and Cookie tests passed!");
    }

    private static MockResponse run(TimezoneValidateFilter filter, TimeServlet servlet,
                                    String timezone, Cookie[] cookies) throws Exception {
        MockRequest request = new MockRequest(timezone, cookies);
        MockResponse response = new MockResponse();
        AtomicBoolean called = new AtomicBoolean();
        FilterChain chain = (req, resp) -> {
            called.set(true);
            servlet.doGet((HttpServletRequest) req, (HttpServletResponse) resp);
        };
        filter.doFilter(request.proxy(), response.proxy(), chain);
        require(called.get(), "Valid request must reach servlet");
        return response;
    }

    private static void checkInvalid(TimezoneValidateFilter filter, TimeServlet servlet,
                                     String input, Cookie[] cookies) throws Exception {
        MockRequest request = new MockRequest(input, cookies);
        MockResponse response = new MockResponse();
        AtomicBoolean called = new AtomicBoolean();
        FilterChain chain = (req, resp) -> called.set(true);
        filter.doFilter(request.proxy(), response.proxy(), chain);
        require(!called.get(), "Invalid query reached servlet");
        require(response.status == 400, "Invalid query must return HTTP 400");
        require(response.body.toString().contains("Invalid timezone"), "Missing error message");
        require(response.cookies.isEmpty(), "Invalid query must not overwrite Cookie");
    }

    private static void checkTime(MockResponse response, String label, ZoneId zone) {
        require(response.status == 200, "Expected HTTP 200");
        require("text/html; charset=UTF-8".equals(response.contentType), "Wrong response content type");
        String html = response.body.toString();
        require(!html.contains("th:text"), "HTML template was not processed by Thymeleaf");
        Matcher match = TIME.matcher(html);
        require(match.find(), "Thymeleaf output has no <time> element");
        String text = match.group(1);
        require(text.endsWith(" " + label), "Wrong timezone label: " + text);
        LocalDateTime actual = LocalDateTime.parse(text.substring(0, 19), FORMAT);
        long seconds = Math.abs(Duration.between(actual, LocalDateTime.now(zone)).getSeconds());
        require(seconds <= 5, "Wrong time for " + label + ": " + text);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MockRequest implements InvocationHandler {
        private final String timezone;
        private final Cookie[] cookies;
        private final Map<String, Object> attributes = new HashMap<>();

        private MockRequest(String timezone, Cookie[] cookies) {
            this.timezone = timezone;
            this.cookies = cookies;
        }

        HttpServletRequest proxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class}, this);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "getParameter" -> "timezone".equals(args[0]) ? timezone : null;
                case "getCookies" -> cookies;
                case "getLocale" -> Locale.ENGLISH;
                case "getContextPath" -> "";
                case "getAttribute" -> attributes.get(args[0]);
                case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
                default -> throw new UnsupportedOperationException("MockRequest: " + method.getName());
            };
        }
    }

    private static final class MockResponse implements InvocationHandler {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body, true);
        private final List<Cookie> cookies = new ArrayList<>();
        private int status = 200;
        private String contentType;

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class}, this);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "getWriter" -> writer;
                case "setStatus" -> { status = (Integer) args[0]; yield null; }
                case "setContentType" -> { contentType = (String) args[0]; yield null; }
                case "addCookie" -> { cookies.add((Cookie) args[0]); yield null; }
                default -> throw new UnsupportedOperationException("MockResponse: " + method.getName());
            };
        }
    }
}
