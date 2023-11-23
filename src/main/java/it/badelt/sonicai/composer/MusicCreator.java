package it.badelt.sonicai.composer;

import it.badelt.sonicai.composer.ai.ArtificialComposer;
import it.badelt.sonicai.composer.sonicpi.SonicPiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MusicCreator {

    @Autowired
    ArtificialComposer artificialComposer;

    @Autowired
    SonicPiClient sonicPiClient;

    public String compose(String description) {
        return artificialComposer.chat(description);
    }

    public void play(String text) {
        String sonicCode = extractCodeFromText(text);
        sonicPiClient.stopCurrentExecution();
        sonicPiClient.sendCode(sonicCode);
    }

    private String extractCodeFromText(String text) {
        if (!text.contains("```")) {
            return text;
        }
        StringBuffer code = new StringBuffer();
        while (text.contains("```ruby")) {
            int startPos = text.indexOf("```ruby") + 7;
            int endPos = text.indexOf("```", startPos);
            if (endPos < 0) {
                endPos = text.length();
            }
            code.append(text.substring(startPos, endPos));
            text = text.substring(endPos + 3);
        }
        return code.toString();
    }
}

