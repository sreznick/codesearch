package codegrep.data;

import java.nio.file.Path;

public record FileReference(Path path, ObjectType type, String content, int startLineno, int endLineno) {
}
