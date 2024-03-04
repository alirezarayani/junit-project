package ir.lazydeveloper.jpodcatcher;

public class PodcastReaderException extends Exception {
    public PodcastReaderException(Throwable exception) {
        super(exception);
    }

    public PodcastReaderException(String message) {
        super(message);
    }
}
