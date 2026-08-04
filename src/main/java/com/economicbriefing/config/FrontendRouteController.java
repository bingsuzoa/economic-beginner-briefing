package com.economicbriefing.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the single-page app for direct requests to public frontend routes. */
@Controller
public class FrontendRouteController {

    @GetMapping({"/privacy", "/privacy/"})
    public String privacy() {
        return "forward:/index.html";
    }
}
