package com.wisehero.springlabs.labs00

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class Lab00DemoController {

    @GetMapping("/lab00")
    fun lab00Demo(): String {
        return "lab00-demo"
    }
}
