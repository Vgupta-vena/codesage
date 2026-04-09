package com.vena.codesage;

import com.vena.codesage.integration.atlassian.AtlassianProperties;
import com.vena.codesage.integration.confluence.ConfluenceProperties;
import com.vena.codesage.integration.jira.JiraProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ConfluenceProperties.class, JiraProperties.class, AtlassianProperties.class})
public class CodeSageApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeSageApplication.class, args);
    }
}
