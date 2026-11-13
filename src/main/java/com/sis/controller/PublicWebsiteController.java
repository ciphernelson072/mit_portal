package com.sis.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicWebsiteController {

    @GetMapping("/")
    public String home() {
        return "forward:/public/index.html";
    }
}
