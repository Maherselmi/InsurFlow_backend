package tn.esprit.insureflow_back.infrastructure.adapter.out.vectorstore;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.domain.port.out.VectorStorePort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MilvusVectorStoreAdapter implements VectorStorePort {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Override
    public void storeDocument(String documentId, String content) {
        if (documentId == null || documentId.isBlank()) {
            throw new RuntimeException("Document id is required");
        }

        if (content == null || content.isBlank()) {
            throw new RuntimeException("Document content is required");
        }

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("documentId", documentId);

        TextSegment segment = TextSegment.from(
                content,
                Metadata.from(metadataMap)
        );

        Embedding embedding = embeddingModel.embed(segment).content();

        embeddingStore.add(embedding, segment);
    }

    @Override
    public List<String> searchSimilarDocuments(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Embedding embedding = embeddingModel.embed(query).content();

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.findRelevant(embedding, 5, 0.60);

        return matches.stream()
                .map(EmbeddingMatch::embedded)
                .filter(segment -> segment != null)
                .map(TextSegment::text)
                .toList();
    }

    @Override
    public void deleteDocument(String documentId) {
        // LangChain4j EmbeddingStore ne supporte pas toujours delete selon l'implémentation.
        // Pour le moment, on garde cette méthode vide.
        // Tu peux l'améliorer plus tard si ton store Milvus supporte suppression par metadata/id.
    }
}