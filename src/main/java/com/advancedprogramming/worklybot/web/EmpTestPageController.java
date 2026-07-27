package com.advancedprogramming.worklybot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmpTestPageController {

    @GetMapping({"/test", "/test/"})
    public String testPage() {
        return "forward:/test/index.html";
    }
}