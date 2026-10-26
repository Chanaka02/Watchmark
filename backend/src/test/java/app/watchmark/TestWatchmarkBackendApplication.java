package app.watchmark;

import org.springframework.boot.SpringApplication;

public class TestWatchmarkBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(WatchmarkBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
