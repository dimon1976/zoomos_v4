package com.java.controller.utils;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/utils/data-merger")
public class DataMergerController {

    @GetMapping
    public String showDataMergerPage() {
        return "utils/data-merger";
    }
}