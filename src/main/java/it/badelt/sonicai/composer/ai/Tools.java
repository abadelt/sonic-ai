package it.badelt.sonicai.composer.ai;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class Tools {

    @Tool("Use this to create a basic drum rhythm (as a live_loop) for a specified number of bars, e.g. a 4-bar pattern or 12-bar pattern)")
    public String getBarStructure(int numberOfBars) {
        String loop = """
                live_loop :basic_rhythm_loop do
                  <#bars>.times do
                    sample :drum_heavy_kick, amp: 0.2
                    sleep 1
                    3.times do
                      sample :drum_snare_soft, amp: 0.1
                      sample :drum_cymbal_closed, amp: 0.1
                      sleep 1
                    end
                  end
                end
                """;
        return loop.replace("<#bars>", String.valueOf(numberOfBars));
    }
}
