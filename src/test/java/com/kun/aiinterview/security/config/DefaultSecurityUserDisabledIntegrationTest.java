package com.kun.aiinterview.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "u2.security-startup-test=true")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class DefaultSecurityUserDisabledIntegrationTest {
    @Autowired ConfigurableApplicationContext context;

    @Test
    void excludesDefaultUserInsteadOfHidingItsPasswordLog(CapturedOutput output) {
        var report = ConditionEvaluationReport.get(context.getBeanFactory());
        assertThat(report.getExclusions()).contains(UserDetailsServiceAutoConfiguration.class.getName());
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(output.getAll()).doesNotContain("Using generated security password:");
    }
}
