package com.shortbridge.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ConventionViewController {

  private static final Set<String> EXCLUDED_PREFIXES =
      Set.of(
          "api", "oauth2", "login", "logout", "static", "assets", "error", "css", "js", "images", "fonts", "files",
          "favicon.ico", "robots.txt", "sitemap.xml", ".well-known", "actuator", "dashboard", "connect", "dev");

  @GetMapping("/{domain:[a-zA-Z][a-zA-Z0-9_-]*}")
  public String list(@PathVariable String domain, HttpServletRequest request) {
    if (EXCLUDED_PREFIXES.contains(domain)) {
      throw notFound();
    }
    return resolveTemplate(domain + "/list");
  }

  @GetMapping("/{domain:[a-zA-Z][a-zA-Z0-9_-]*}/new")
  public String create(@PathVariable String domain) {
    if (EXCLUDED_PREFIXES.contains(domain)) {
      throw notFound();
    }
    return resolveTemplate(domain + "/create");
  }

  @GetMapping("/{domain:[a-zA-Z][a-zA-Z0-9_-]*}/{id}/edit")
  public String edit(@PathVariable String domain, @PathVariable String id) {
    if (EXCLUDED_PREFIXES.contains(domain)) {
      throw notFound();
    }
    return resolveTemplate(domain + "/edit");
  }

  private String resolveTemplate(String path) {
    ClassPathResource resource = new ClassPathResource("templates/" + path + ".html");
    if (!resource.exists()) {
      throw notFound();
    }
    return path;
  }

  private static org.springframework.web.server.ResponseStatusException notFound() {
    return new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.NOT_FOUND);
  }
}
