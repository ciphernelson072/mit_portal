package com.sis.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class PublicSearchController {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/", "/public/index.html", "/public/about.html", "/public/programmes.html",
            "/public/admissions.html", "/public/contact.html", "/admissions.html"
    );
    private static final List<String> PRIVATE_PATHS = List.of(
            "/portal-gateway.html", "/index.html", "/dashboard.html", "/academics.html", "/attendance.html",
            "/finance.html", "/campus-support.html", "/profile.html", "/admin-dashboard.html",
            "/admin-login.html", "/teacher-dashboard.html", "/operations.html",
            "/file-management.html", "/api/"
    );

    private final String publicBaseUrl;

    public PublicSearchController(@Value("${app.frontend.url:http://localhost:8080}") String frontendUrl) {
        URI base = URI.create(frontendUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null
                || base.getUserInfo() != null
                || (base.getPath() != null && !base.getPath().isEmpty() && !"/".equals(base.getPath()))
                || base.getQuery() != null
                || base.getFragment() != null) {
            throw new IllegalArgumentException("app.frontend.url must be an absolute HTTP(S) site origin");
        }
        this.publicBaseUrl = frontendUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        PUBLIC_PATHS.forEach(path -> xml.append("<url><loc>")
                .append(publicBaseUrl).append(path)
                .append("</loc></url>"));
        return xml.append("</urlset>").toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        StringBuilder content = new StringBuilder("User-agent: *\nAllow: /\n");
        PRIVATE_PATHS.forEach(path -> content.append("Disallow: ").append(path).append('\n'));
        return content.append("Sitemap: ").append(publicBaseUrl).append("/sitemap.xml\n").toString();
    }
}
