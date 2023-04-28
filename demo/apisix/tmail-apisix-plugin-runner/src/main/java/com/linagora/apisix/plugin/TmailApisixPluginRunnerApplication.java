package com.linagora.apisix.plugin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.linagora.apisix.plugin", "org.apache.apisix.plugin.runner"})
public class TmailApisixPluginRunnerApplication {

	public static void main(String[] args) {
		SpringApplication.run(TmailApisixPluginRunnerApplication.class, args);
	}

}
