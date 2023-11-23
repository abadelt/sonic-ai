package it.badelt.sonicai.composer.ai;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("sonicai")
@Getter
public class AppConfiguration {

    private final List<String> urlsToEmbed = new ArrayList<>();
    @Getter
    public class EmbeddingUrl {
        private String prefix;
        private String rootPath;
    }
}
