package com.vena.codesage.config;

import com.vena.codesage.graph.service.ranking.RankingWeights;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(RankingWeights.class)
public class SemanticBuildConfig {

    @Bean(name= "semanticBuildExecutor", destroyMethod = "close")
    public ExecutorService semanticBuildExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
