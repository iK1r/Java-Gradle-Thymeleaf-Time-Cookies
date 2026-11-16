package com.example.timeservlet;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@WebServlet("/time")
public class TimeServlet extends HttpServlet {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private TemplateEngine templateEngine;

    @Override
    public void init() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // The filter has already checked the query parameter and the saved Cookie.
        TimezoneResolver.ResolvedTimezone timezone =
                (TimezoneResolver.ResolvedTimezone) req.getAttribute(
                        TimezoneValidateFilter.TIMEZONE_ATTRIBUTE);
        if (timezone == null) {
            timezone = TimezoneResolver.resolve(null);
        }

        String currentTime = ZonedDateTime.now(timezone.zoneId()).format(TIME_FORMAT);

        Context context = new Context(req.getLocale());
        context.setVariable("currentTime", currentTime);
        context.setVariable("timezone", timezone.label());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/html; charset=UTF-8");
        templateEngine.process("time", context, resp.getWriter());
    }
}
