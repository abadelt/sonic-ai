package it.badelt.sonicai.composer.sonicpi;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SonicPiClient {

    protected static final String SERVER_LOG_PATH = System.getProperty("user.home") + "/.sonic-pi/log/spider.log";
    private long apiToken;
    private int serverPortNumber;
    private int guiPortNumber;

    OSCPortOut sonicPort;
    OSCPortOut guiPort;
    public SonicPiClient() {
        configure();
        OSCPortOutBuilder builder = new OSCPortOutBuilder();
        // builder.setRemoteSocketAddress(new InetSocketAddress("localhost", SERVER_PORT));
        builder.setRemotePort(serverPortNumber);
        try {
            sonicPort = builder.build();
            sonicPort.connect();
        } catch (IOException e) {
            throw new RuntimeException("Cannot connect to Sonic Pi server: {}" + e.getMessage(), e.getCause());
        }
        builder = new OSCPortOutBuilder();
        builder.setRemotePort(guiPortNumber);
        try {
            guiPort = builder.build();
            guiPort.connect();
        } catch (IOException e) {
            throw new RuntimeException("Cannot connect to Sonic Pi GUI: {}" + e.getMessage(), e.getCause());
        }
        // makeDemoCalls();
    }

    public void stopCurrentExecution() {
        try {
            final List<Object> args = new ArrayList<>(2);
            args.add(apiToken);
            OSCMessage message = new OSCMessage("/stop-all-jobs", args);
            sonicPort.send(message);
        } catch (IOException | OSCSerializeException e) {
            throw new RuntimeException("Cannot send code to Sonic Pi server: {}" + e.getMessage(), e.getCause());
        }
    }

    public void sendCode(String code) {
        try {
            final List<Object> args = new ArrayList<>(2);
            args.add(apiToken);
            args.add(code);
            // gui.send("/syntax_error", message[:jobid], desc, error_line, line, line.to_s)
            // gui.send("/buffer/replace", buf_id, content, line, index, first_line);
            OSCMessage message = new OSCMessage("/run-code", args);
            // OSCMessage message = new OSCMessage("/syntax_error", args);
            guiPort.send(message);

            args.clear();
            args.add(apiToken);
            args.add(code);
            message = new OSCMessage("/run-code", args);
            sonicPort.send(message);
        } catch (IOException | OSCSerializeException e) {
            throw new RuntimeException("Cannot send code to Sonic Pi server: {}" + e.getMessage(), e.getCause());
        }
    }

    private void makeDemoCalls() {
        sendCode(" live_loop :mycode do\n  sample :bd_haus, cutoff: 110, amp: 1.5\n  sleep 1\n end");
        sendCode(" live_loop :mycode do\n  sample :bd_haus, cutoff: 110, amp: 2.5\n  sleep 2\n end");
        sendCode(" live_loop :mycode2 do\n sync :mycode\n sample :drum_cymbal_closed, amp: 0.8\n end");
    }

    private void configure() {
        serverPortNumber = retrieveServerPort();
        guiPortNumber = serverPortNumber + 1;
        apiToken = retrieveApiToken();
    }

    private int retrieveServerPort() {
        String portsLine = findLineInLogFile(SERVER_LOG_PATH, ":server_port=>");
        if (portsLine == null) {
            throw new RuntimeException("Cannot configure SonicPiClient - server port not found in log.");
        }
        String regexPattern = ":server_port=>(\\d+),";
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(portsLine);
        if (!matcher.find()) {
            throw new RuntimeException("Cannot configure SonicPiClient - server port not found in log.");
        }
        String matchedValue = matcher.group(1);
        try {
            return Integer.parseInt(matchedValue);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Cannot configure SonicPiClient - server port not found in log.");
        }
    }

    private static long retrieveApiToken() {
        String tokenLine = findLineInLogFile(SERVER_LOG_PATH, "Token:");
        if (tokenLine == null) {
            throw new RuntimeException("Cannot configure SonicPiClient - API token  not found in log.");
        }
        String regexPattern = "^Token:\\s?(-?\\d+).*$";
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(tokenLine);
        if (!matcher.find()) {
            throw new RuntimeException("Cannot configure SonicPiClient - API token not found in log.");
        }
        String matchedValue = matcher.group(1);
        try {
            return Long.parseLong(matchedValue);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Cannot configure SonicPiClient - API token not found in log.");
        }
    }

    public static String findLineInLogFile(String filePath, String searchString) {
        String lineContainingString = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(searchString)) {
                    lineContainingString = line;
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return lineContainingString;
    }

}
