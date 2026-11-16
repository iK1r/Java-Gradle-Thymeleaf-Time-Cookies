package com.example.timeservlet;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/time")
public class TimezoneValidateFilter extends HttpFilter {
    public static final String TIMEZONE_ATTRIBUTE = "validatedTimezone";

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String timezoneParameter = req.getParameter("timezone");
        TimezoneResolver.ResolvedTimezone timezone;

        if (timezoneParameter != null) {
            // The query parameter has priority over the stored Cookie.
            try {
                timezone = TimezoneResolver.resolve(timezoneParameter);
            } catch (IllegalArgumentException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("text/html; charset=UTF-8");
                resp.getWriter().write("<!DOCTYPE html><html lang=\"en\"><head>" +
                        "<meta charset=\"UTF-8\"><title>Invalid timezone</title>" +
                        "</head><body>Invalid timezone</body></html>");
                return;
            }
            // Only a valid, explicitly passed timezone can update lastTimezone.
            TimezoneCookieService.save(req, resp, timezone.label());
        } else {
            String savedTimezone = TimezoneCookieService.read(req);
            try {
                timezone = TimezoneResolver.resolve(savedTimezone);
            } catch (IllegalArgumentException e) {
                // Cookies are controlled by the client: ignore an invalid value.
                timezone = TimezoneResolver.resolve(null);
            }
        }

        req.setAttribute(TIMEZONE_ATTRIBUTE, timezone);
        chain.doFilter(req, resp);
    }
}
