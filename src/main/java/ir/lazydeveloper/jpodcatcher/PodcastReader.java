package ir.lazydeveloper.jpodcatcher;

import ir.lazydeveloper.jpodcatcher.model.Channel;

public interface PodcastReader {
    Channel loadRSS(String uri) throws PodcastReaderException;
}
