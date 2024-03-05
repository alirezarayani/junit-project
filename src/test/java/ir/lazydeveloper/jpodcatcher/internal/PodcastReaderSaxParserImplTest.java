package ir.lazydeveloper.jpodcatcher.internal;

import ir.lazydeveloper.jpodcatcher.PodcastReaderException;
import ir.lazydeveloper.jpodcatcher.model.Channel;
import ir.lazydeveloper.jpodcatcher.model.Item;
import ir.lazydeveloper.jpodcatcher.model.itunes.ItunesChannelData;
import ir.lazydeveloper.jpodcatcher.model.itunes.ItunesItemData;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PodcastReaderSaxParserImplTest {

    static PodcastReaderSaxParserImpl podcastReaderSaxParser;
    static String samplePodcastURI;
    static String podcastWithItunesURI;

    /**
     * This method must be static
     * And runs once
     */
    @BeforeAll
    static void beforeAll() {
        samplePodcastURI = PodcastReaderSaxParserImplTest.class.getClassLoader().getResource("simple_podcast.xml").toString();
        podcastWithItunesURI = PodcastReaderSaxParserImplTest.class.getClassLoader().getResource("podcast_with_itunes.rss").toString();
    }

    /**
     * This method runs before each testcase
     */
    @BeforeEach
    void beforeEach() {
        podcastReaderSaxParser = new PodcastReaderSaxParserImpl();
    }

    /**
     * This method runs after each testcase
     */
    @AfterEach
    void tearDown() {
    }

    /**
     * Each unit test must have @Test annotation
     * must be void
     * minimum visibility is package private
     * cannot be abstract
     */
    @Test
    void loadWrongRSSURIShouldThrowException() {
        assertThrows(PodcastReaderException.class, () -> podcastReaderSaxParser.loadRSS("WRONG_URI"));
    }

    @Test
    void loadRSSShouldContainChannelDate() throws PodcastReaderException {
        Channel channel = podcastReaderSaxParser.loadRSS(samplePodcastURI);
        assertAll(
                () -> assertEquals("Raw Data", channel.title()),
                () -> assertEquals("http://www.rawdatapodcast.com", channel.link()),
                () -> assertEquals("We’ve entered a new era.", channel.description()),
                () -> assertEquals("Thu, 21 Nov 2019 09:00:00 -0000", channel.pubDate()),
                () -> assertEquals("Wed, 17 Mar 2021 19:22:02 -0000", channel.lastBuildDate()),
                () -> assertEquals("en", channel.language()),
                () -> assertEquals("All rights reserved", channel.copyright()),
                () -> assertEquals("PRX Feeder v1.0.0", channel.generator()),
                () -> assertEquals("https://f.prxu.org/190/images/RawData_ForWeb_RGB.png", channel.image().url()),
                () -> assertEquals("Raw Data", channel.image().title()),
                () -> assertEquals("http://www.rawdatapodcast.com", channel.image().link())
        );
    }

    @Test
    void loadRSSShouldContainEpisodesData() throws PodcastReaderException {
        Channel channel = podcastReaderSaxParser.loadRSS(samplePodcastURI);
        assertEquals(2, channel.items().size(), "the size of items must be equal two");
        Item firstItem = channel.items().get(0);
        assertAll(
                () -> assertEquals("b970-9f45620b0fd1", firstItem.guid()),
                () -> assertEquals("Technically Sweet", firstItem.title()),
                () -> assertEquals("Thu, 21 Nov 2019 09:00:00 -0000", firstItem.pubDate()),
                () -> assertEquals("https://beta.prx.org/stories/295275", firstItem.link()),
                () -> assertNotNull(firstItem.enclosure()),
                () -> assertEquals("https://dts.podtrac.com/Technically_Sweet_P1_Raw_Data.mp3", firstItem.enclosure().url()),
                () -> assertEquals("audio/mpeg", firstItem.enclosure().type()),
                () -> assertEquals(39374396L, firstItem.enclosure().length()),
                () -> assertEquals(Arrays.asList("Blockchain", "Charity ryerson"), firstItem.categories())
        );
    }

    /**
     * @Nested tests give the test writer more capabilities to express the relationship among several groups of tests.
     */
    @Nested
    class ItunesPodcastElementTest {
        @Test
        void loadRSSShouldContainItunesData() throws PodcastReaderException {
            Channel channel = podcastReaderSaxParser.loadRSS(podcastWithItunesURI);
            ItunesChannelData itunes = channel.itunesChannelData();
            assertAll(
                    () -> assertEquals("FeedForAll Mac OS Team", itunes.author())
                    , () -> assertEquals("RSS Feed Podcast", itunes.title())
                    , () -> assertEquals("serial", itunes.type())
                    , () -> assertEquals("No", itunes.block())
                    , () -> assertEquals("No", itunes.complete())
                    , () -> assertEquals("False", itunes.explicit())
                    , () -> assertEquals("https://applehosted.podcasts.apple.com/hiking_treks/artwork.png", itunes.image())
                    , () -> assertEquals("https://newlocation.com/example.rss", itunes.newFeedUrl())
                    , () -> assertEquals("Technology", itunes.category().category())
                    , () -> assertEquals(Collections.singletonList("Information Technology"), itunes.category().subCategories())
                    , () -> assertEquals("FeedForAll Mac OS Team", itunes.owner().name())
                    , () -> assertEquals("macsupport@feedforall.com", itunes.owner().email())
            );
        }
        @Test
        void loadRSSShouldContainItunesEpisodesData() throws PodcastReaderException {
            Channel channel = podcastReaderSaxParser.loadRSS(podcastWithItunesURI);
            ItunesItemData itunes = channel.items().get(0).itunesItemData();
            assertAll(
                    () -> assertEquals("4", itunes.episode())
                    , () -> assertEquals("1", itunes.season())
                    , () -> assertEquals("trailer", itunes.episodeType())
                    , () -> assertEquals("Hiking Treks Trailer", itunes.title())
                    , () -> assertEquals("1079", itunes.duration())
                    , () -> assertEquals("https://applehosted.podcasts.apple.com/hiking_treks/artwork2.png", itunes.image())
                    , () -> assertEquals("No", itunes.block())
            );
        }
    }
}
