package codegrep.data;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ProjectContents(List<FileReference> references) {
    public Map<Path, List<FileReference>> getLiterals(String toGrep) {
        return references
                .stream()
                .filter(d -> d.type() == ObjectType.STRING_LITERAL && d.content().contains(toGrep))
                .collect(Collectors.groupingBy(FileReference::path));
    }
}
