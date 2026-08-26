package com.example.banking.gateway;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class PresenterController {
    private final PresenterControlService controls;

    public PresenterController(PresenterControlService controls) {
        this.controls = controls;
    }

    @GetMapping("/state")
    Map<String, Object> state() {
        return controls.state();
    }

    @PostMapping("/control")
    Map<String, Object> control(@RequestBody PresenterControlService.ControlRequest request) {
        return controls.apply(request);
    }
}
