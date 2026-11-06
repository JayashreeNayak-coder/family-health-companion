package com.familyhealth.extraction;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the extraction worker's poll. Tests turn the worker off and call it directly. */
@Configuration
@EnableScheduling
class SchedulingConfig {

}
