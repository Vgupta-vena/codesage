package com.vena.sageorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@SpringBootApplication
@Modulith
public class BrainOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrainOrchestratorApplication.class, args);
    }
}
