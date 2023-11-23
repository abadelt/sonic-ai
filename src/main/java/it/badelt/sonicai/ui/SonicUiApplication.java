package it.badelt.sonicai.ui;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "it.badelt.sonicai")
public class SonicUiApplication {

	public static void main(String[] args) {
		Application.launch(ComposerApplication.class, args);
	}

}
