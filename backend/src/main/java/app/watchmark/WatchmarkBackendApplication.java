package app.watchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WatchmarkBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WatchmarkBackendApplication.class, args);
	}

}
