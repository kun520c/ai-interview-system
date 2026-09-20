package com.kun.aiinterview.common.recovery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StaleRecoveryProperties.class)
public class RecoveryConfiguration {
}
