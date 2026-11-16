package com.example.timeservlet;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Reading and saving the user's preferred timezone in a browser Cookie. */
public final class TimezoneCookieService {
    public static final String COOKIE_NAME = "lastTimezone";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

    private TimezoneCookieService() {
    }

    public static String read(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void save(HttpServletRequest req, HttpServletResponse resp, String timezone) {
        Cookie cookie = new Cookie(COOKIE_NAME, timezone);
        String contextPath = req.getContextPath();
        cookie.setPath(contextPath == null || contextPath.isEmpty() ? "/" : contextPath);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);
    }
}
