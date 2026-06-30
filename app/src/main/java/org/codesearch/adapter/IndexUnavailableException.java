package org.codesearch.adapter;

import java.io.IOException;

public class IndexUnavailableException extends IOException {
    public IndexUnavailableException(String message) {
        super(message);
    }

    public IndexUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
