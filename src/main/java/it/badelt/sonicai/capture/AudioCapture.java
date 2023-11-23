package it.badelt.sonicai.capture;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

@Service
@Slf4j
@EnableAsync
public class AudioCapture {

    private final static String URL = "https://api.openai.com/v1/audio/transcriptions";
    public final static int MAX_ALLOWED_SIZE = 25 * 1024 * 1024;
    public final static int MAX_CHUNK_SIZE_BYTES = 20 * 1024 * 1024;
    private final static String MODEL = "whisper-1";

    @Value("${langchain4j.whisper.openai.api-key:default}")
    private String apiKey;

    String filePath = "/Users/andreas/Downloads/audio.wav";

    TargetDataLine line = null;

    @Async
    public void startCapture() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            if (!AudioSystem.isLineSupported(info)) {
                log.warn("Audio line is not supported. Throwing RuntimeException.");
                throw new RuntimeException("Audio line is not supported.");
            }

            File file = new File(filePath);

            log.debug("Starting capturing.");

            line.open(format);
            line.start();

            AudioInputStream audioInputStream = new AudioInputStream(line);
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, new File(filePath));

            log.debug("Capturing started.");
        } catch (Exception e) {
            log.error("Could not start capture: ", e);
        }
    }

    public void stopCapture() {
        if (line == null) {
            log.warn("Stop capture called with line == null, doing nothing.");
            return;
        }
        line.stop();
        line.close();
        log.debug("Audio line stopped.");

    }

    public String transcribeLastCapture() {
        return transcribeFile("Please transcribe this audio to text", filePath);
    }

    public String transcribeFile(String prompt, String filePath) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            File file = new File(filePath);

            HttpPost httpPost = new HttpPost(URL);
            httpPost.setHeader("Authorization", "Bearer %s".formatted(apiKey));

            HttpEntity entity = MultipartEntityBuilder.create()
                    .setContentType(ContentType.MULTIPART_FORM_DATA)
                    .addPart("file", new FileBody(file, ContentType.DEFAULT_BINARY))
                    .addPart("model", new StringBody(MODEL, ContentType.DEFAULT_TEXT))
                    .addPart("response_format", new StringBody("text", ContentType.DEFAULT_TEXT))
                    .addPart("prompt", new StringBody(prompt, ContentType.DEFAULT_TEXT))
                    .build();
            httpPost.setEntity(entity);

            return client.execute(httpPost, response -> {
                System.out.println("Status: " + new StatusLine(response));
                String text = EntityUtils.toString(response.getEntity());
                log.info("Transcriber returned this text:\n" + text);
                return text;
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
