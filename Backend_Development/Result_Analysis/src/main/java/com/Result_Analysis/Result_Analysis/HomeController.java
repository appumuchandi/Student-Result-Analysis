package com.Result_Analysis.Result_Analysis;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/")
    public String home(){return "Result Analysis Backend is Running Successfully!";}
}
