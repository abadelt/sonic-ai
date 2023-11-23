package it.badelt.sonicai.composer.ai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.net.URL;

import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.UrlDocumentLoader.load;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;

@Configuration
@Slf4j
public class ToolChainConfig {

    @Autowired
    Tools tools;

    @Autowired
    FileSystemEmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    AppConfiguration config;

    @Bean
    ArtificialComposer artificialComposer(ChatLanguageModel chatLanguageModel, Retriever<TextSegment> retriever) {
        return AiServices.builder(ArtificialComposer.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(4))
                .tools(tools)
                .retriever(retriever)
                .build();
    }

    @Bean
    Retriever<TextSegment> retriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {

        // You will need to adjust these parameters to find the optimal setting, which will depend on two main factors:
        // - The nature of your data
        // - The embedding model you are using
        int maxResultsRetrieved = 2;
        double minScore = 0.9;

        initEmbeddingStore(embeddingModel);

        return EmbeddingStoreRetriever.from(embeddingStore, embeddingModel, maxResultsRetrieved, minScore);
    }

    EmbeddingStore<TextSegment> initEmbeddingStore(EmbeddingModel embeddingModel) {

        try {
            URL base = Thread.currentThread().getContextClassLoader().getResource("knowledgebase/");
            File[] resources = new File(base.getPath()).listFiles();
            for (File resource : resources) {
                createEmbedding(embeddingModel, resource);
            }

            for (String urlAsString : config.getUrlsToEmbed()) {
                URL url = new URL(urlAsString);
                createEmbedding(embeddingModel, url);
            }

            embeddingStore.writeFileStore();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return embeddingStore;
    }

    private void createEmbedding(EmbeddingModel embeddingModel, URL resource) {
        if (embeddingStore.isAlreadyAdded(resource)) {
            return;
        }
        log.debug("Adding resource to embeddings: " + resource.toString());
        final Document document = load(resource.toExternalForm());
        splitAndIngest(embeddingModel, document);
    }

    private void createEmbedding(EmbeddingModel embeddingModel, File resource) {
        if (resource.isDirectory()) {
            for (File subResource : resource.listFiles()) {
                createEmbedding(embeddingModel, subResource);
            }
            return;
        }
        if (embeddingStore.isAlreadyAdded(resource)) {
            return;
        }
        log.debug("Adding resource to embeddings: " + resource.toString());
        final Document document = loadDocument(resource.getPath());
        splitAndIngest(embeddingModel, document);
    }

    private void splitAndIngest(EmbeddingModel embeddingModel, Document document) {
        // 1. Split the document into segments of the specified number of tokens
        // 2. Convert segments into embeddings
        // 3. Store embeddings into embedding store
        // All this can be done manually, but we will use EmbeddingStoreIngestor to automate this:
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(600, 100, new OpenAiTokenizer(GPT_3_5_TURBO));
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

}
