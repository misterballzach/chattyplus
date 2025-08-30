package chatty.util;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import java.io.IOException;
import java.util.function.Consumer;

public class GeminiHelper {

    private final String apiKey;

    public GeminiHelper(String apiKey) {
        this.apiKey = apiKey;
    }

    public void generateContent(String personality, String prompt, Consumer<String> onResult) {
        try (VertexAI vertexAI = new VertexAI("chatty-gemini", apiKey)) {
            GenerativeModel model = new GenerativeModel("gemini-pro", vertexAI);
            GenerateContentResponse response = model.generateContent(personality + "\n\n" + prompt);
            onResult.accept(ResponseHandler.getText(response));
        } catch (IOException e) {
            e.printStackTrace();
            onResult.accept("Error: " + e.getMessage());
        }
    }
}
