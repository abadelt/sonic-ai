package it.badelt.sonicai.ui;

import it.badelt.sonicai.capture.AudioCapture;
import it.badelt.sonicai.composer.MusicCreator;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Background;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
@FxmlView("/composer.fxml")
@Slf4j
public class ComposerController {

    @Autowired
    MusicCreator musicCreator;

    @Autowired
    AudioCapture audioCapture;

    @FXML
    public void initialize() {
        log.debug("Initializing controller for composer UI.");

        // Automatic binding of methods not working, hence binding explicitily here:
        recordButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                handleRecordButton(e);
            }
        });
        submitDescriptionButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                handleSubmitDescriptionButton(e);
            }
        });
        resubmitToLMButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                handleResubmitToLmButton(e);
            }
        });
        playButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                handlePlayButton(e);
            }
        });
    }

    @FXML
    private Button recordButton;
    private boolean recording = false;

    @FXML
    private TextArea description;

    @FXML
    private Button submitDescriptionButton;

    @FXML
    private TextArea sonicCode;

    @FXML
    private Button resubmitToLMButton;

    @FXML
    private Button playButton;



    @FXML
    private void handleRecordButton(ActionEvent event) {
        if (!recording) {
            recording = true;
            recordButton.setText("Stop Recording");
            recordButton.setStyle("-fx-background-color: #ff4444");
            audioCapture.startCapture();
        } else {
            recording = false;
            recordButton.setText("Start Recording");
            audioCapture.stopCapture();
            recordButton.setStyle("-fx-background-color: #bbbbbb");
            String text = null;
            try {
                text = CompletableFuture.supplyAsync(() -> audioCapture.transcribeLastCapture()).handle((s, t) -> s != null ? s : "").get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            description.setText(text);
        }
    }

    @FXML
    private void handleSubmitDescriptionButton(ActionEvent event) {
        submitDescriptionButton.setDisable(true);
        String input = description.getText();
        log.debug("Submitting description to composer: " + input);
        sonicCode.setText("");
        CompletableFuture.runAsync(() -> {
            String response = musicCreator.compose(input);
            log.debug("Received code from composer: " + response);
            sonicCode.setText(response);
            submitDescriptionButton.setDisable(false);
        });
    }

    @FXML
    private void handleResubmitToLmButton(ActionEvent event) {
        // Dummy event handler for Submit Button 2
        System.out.println("RESUBMIT - Text from TextArea 2: " + sonicCode.getText());
    }

    private void handlePlayButton(ActionEvent e) {
        String text = sonicCode.getText();
        musicCreator.play(text);
    }

}
