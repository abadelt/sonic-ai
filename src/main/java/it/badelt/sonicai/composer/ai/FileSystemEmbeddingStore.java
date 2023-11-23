package it.badelt.sonicai.composer.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Comparator.comparingDouble;

@Service
@Slf4j
public class FileSystemEmbeddingStore<Embedded extends TextSegment> implements EmbeddingStore<Embedded> {

    @Value("${user.home}/.sonic-ai")
    String storagePath;

    File storageFolder;

    private static class Entry<Embedded> implements Serializable{

        String id;
        Embedding embedding;
        Embedded embedded;

        Entry(String id, Embedding embedding, Embedded embedded) {
            this.id = id;
            this.embedding = embedding;
            this.embedded = embedded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?> that = (Entry<?>) o;
            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.embedding, that.embedding)
                    && Objects.equals(this.embedded, that.embedded);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, embedding, embedded);
        }
    }

    private List<Entry<Embedded>> entries = new ArrayList<>();

    Set<String> storedEmbeddings = new HashSet();

    @PostConstruct
    public boolean readFileStore() {
        try {
            storageFolder = new File(storagePath);
            if (!storageFolder.exists()) {
                storageFolder.mkdirs();
            }
            File file = new File(storageFolder + "/embeddings.store");
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            entries = (List<Entry<Embedded>>) ois.readObject();
            for (Entry<Embedded> entry : entries.stream().toList()) {
                storedEmbeddings.add(entry.id);
            }
            return true;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            log.error("Initializing FileSystemEmbeddingStore failed with exception: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void writeFileStore() {
        // Assert.isTrue(storageFolder.isDirectory(), storagePath + " is not a directory.");
        try {
            FileOutputStream fos = new FileOutputStream(storageFolder + "/embeddings.store");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(entries);
        } catch (IOException e) {
            log.error("Writing to FileSystemEmbeddingStore failed with exception: {}", e.getMessage());
        }
    }


    public static final String MD_KEY_ABSOLUTE_DIRECTORY_PATH = "absolute_directory_path";
    public static final String MD_KEY_FILE_NAME = "file_name";
    public static final String MD_KEY_INDEX = "index";
    public static final String MD_KEY_URL = "url";

    public boolean isAlreadyAdded(Embedded embedded) {
        return storedEmbeddings.contains(createKey(embedded));
    }

    public boolean isAlreadyAdded(File file) {
        return storedEmbeddings.contains(createKey(file));
    }

    public boolean isAlreadyAdded(URL url) {
        return storedEmbeddings.contains(createKey(url));
    }

    public boolean isAlreadyAdded(String id) {
        return storedEmbeddings.contains(id);
    }

    private String createKey(Embedded embedded) {
        StringBuffer key = new StringBuffer();
        Metadata md = embedded.metadata();
        if (md.get(MD_KEY_ABSOLUTE_DIRECTORY_PATH) != null) {
            key.append("file:");
            key.append(md.get(MD_KEY_ABSOLUTE_DIRECTORY_PATH)  + "/");
            key.append(md.get(MD_KEY_FILE_NAME) + ":" + md.get(MD_KEY_INDEX));
        } else if (md.get(MD_KEY_URL) != null) {
            key.append(md.get(MD_KEY_URL) + ":" + md.get(MD_KEY_INDEX));
        } else {
            key.append("unknown:" + md.toString() + ":" + md.get(MD_KEY_INDEX));
        }
        return key.toString();
    }

    private String createKey(File file) {
        return "file:" + file.getPath() + ":0";
    }

    private String createKey(URL url) {
        return url.toString() + ":0";
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        String id = embedded != null ? createKey(embedded) : randomUUID();
        add(id, embedding, embedded);
        return id;
    }


    private void add(String id, Embedding embedding, Embedded embedded) {
        if (isAlreadyAdded(id)) {
            return;
        }
        entries.add(new Entry<>(id, embedding, embedded));
        storedEmbeddings.add(id);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>();
        for (Embedding embedding : embeddings) {
            ids.add(add(embedding));
        }
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(add(embeddings.get(i), embedded.get(i)));
        }
        return ids;
    }

    @Override
    public List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {

        Comparator<EmbeddingMatch<Embedded>> comparator = comparingDouble(EmbeddingMatch::score);
        PriorityQueue<EmbeddingMatch<Embedded>> matches = new PriorityQueue<>(comparator);

        for (Entry<Embedded> entry : entries) {
            double cosineSimilarity = CosineSimilarity.between(entry.embedding, referenceEmbedding);
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);
            if (score >= minScore) {
                matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, entry.embedded));
                if (matches.size() > maxResults) {
                    matches.poll();
                }
            }
        }

        List<EmbeddingMatch<Embedded>> result = new ArrayList<>(matches);
        result.sort(comparator);
        Collections.reverse(result);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileSystemEmbeddingStore<?> that = (FileSystemEmbeddingStore<?>) o;
        return Objects.equals(this.entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    public String serializeToJson() {
        return new Gson().toJson(this);
    }

    public void serializeToFile(Path filePath) {
        try {
            String json = serializeToJson();
            Files.write(filePath, json.getBytes(), CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void serializeToFile(String filePath) {
        serializeToFile(Paths.get(filePath));
    }

    public static FileSystemEmbeddingStore<TextSegment> fromJson(String json) {
        Type type = new TypeToken<FileSystemEmbeddingStore<TextSegment>>() {
        }.getType();
        return new Gson().fromJson(json, type);
    }

    public static FileSystemEmbeddingStore<TextSegment> fromFile(Path filePath) {
        try {
            String json = new String(Files.readAllBytes(filePath));
            return fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static FileSystemEmbeddingStore<TextSegment> fromFile(String filePath) {
        return fromFile(Paths.get(filePath));
    }

}
