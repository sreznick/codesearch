package codegrep.data;

import java.nio.file.Path;

// TODO: storing lineno here is problematic for cache erasure purposes
//  -- should be changed when Lucene is integrated
public record FileReference(Path path, ObjectType type, String content, int startLineno, int endLineno) {
}
