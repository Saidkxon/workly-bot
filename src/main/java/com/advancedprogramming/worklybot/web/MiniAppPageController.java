package com.advancedprogramming.worklybot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MiniAppPageController {

    @GetMapping({"/app", "/app/"})
    public String miniApp() {
        return "redirect:/app/index.html";
    }
}
