package codegrep.data;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public record ProjectContents(List<FileReference> references) {
    public static Map<Path, List<Document>> getDocuments(List<FileReference> references) {
        return references
                .stream()
                .collect(Collectors.groupingBy(FileReference::path))
                .entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey, 
                        e -> e.getValue().stream().map(ProjectContents::convertToDocument).toList()));
    }

    public static Document convertToDocument(FileReference ref) {
        Document doc = new Document();

        doc.add(new StringField("type", ref.type().name(), Store.YES));
        doc.add(new StringField("path", ref.path().toString(), Store.YES));
        doc.add(new TextField("content", ref.content(), Store.YES));
        doc.add(new IntField("lineno", ref.startLineno(), Store.YES));
        doc.add(new IntField("endLineno", ref.endLineno(), Store.YES));

        return doc;
    }

    public static FileReference convertToFileRef(Document doc) {
        ObjectType type = ObjectType.valueOf(doc.get("type"));
        Path path = Path.of(doc.get("path"));
        String content = doc.get("content");
        int startLineno = doc.getField("lineno").numericValue().intValue();
        int endLineno = doc.getField("endLineno").numericValue().intValue();
        return new FileReference(path, type, content, startLineno, endLineno);
    }
}
