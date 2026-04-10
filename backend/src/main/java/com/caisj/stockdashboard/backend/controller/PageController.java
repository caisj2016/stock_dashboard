package com.caisj.stockdashboard.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("currentPage", "dashboard");
        model.addAttribute("apiBase", "");
        return "index";
    }

    @GetMapping("/screener")
    public String screener(Model model) {
        model.addAttribute("currentPage", "screener");
        model.addAttribute("apiBase", "");
        return "screener";
    }

    @GetMapping("/chart")
    public String chart(
        @RequestParam(required = false) String symbol,
        @RequestParam(required = false) String name,
        Model model
    ) {
        model.addAttribute("currentPage", "chart");
        model.addAttribute("apiBase", "");
        model.addAttribute("symbol", symbol == null ? "" : symbol);
        model.addAttribute("name", name == null ? "" : name);
        return "chart";
    }

    @GetMapping("/short-interest")
    public String shortInterest(
        @RequestParam(required = false) String symbol,
        @RequestParam(required = false) String name,
        Model model
    ) {
        model.addAttribute("currentPage", "short_interest");
        model.addAttribute("apiBase", "");
        model.addAttribute("symbol", symbol == null ? "6758.T" : symbol);
        model.addAttribute("name", name == null ? "" : name);
        return "short_interest";
    }

    @GetMapping("/chart-smoke")
    public String chartSmoke(Model model) {
        model.addAttribute("currentPage", "chart_smoke");
        model.addAttribute("apiBase", "");
        return "chart_smoke";
    }

    @GetMapping("/ownership-short-test")
    public String ownershipShortTest(
        @RequestParam(required = false) String symbol,
        Model model
    ) {
        model.addAttribute("currentPage", "ownership_short_test");
        model.addAttribute("apiBase", "");
        model.addAttribute("symbol", symbol == null ? "6758.T" : symbol);
        return "ownership_short_test";
    }
}
