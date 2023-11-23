package it.badelt.sonicai.composer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;


import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.UrlDocumentLoader.load;

//@SpringBootApplication
@Slf4j
public class SonicAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SonicAiApplication.class, args);
	}

}
