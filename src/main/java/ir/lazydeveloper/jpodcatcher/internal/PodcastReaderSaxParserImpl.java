package ir.lazydeveloper.jpodcatcher.internal;


import ir.lazydeveloper.jpodcatcher.PodcastReader;
import ir.lazydeveloper.jpodcatcher.PodcastReaderException;
import ir.lazydeveloper.jpodcatcher.model.Channel;
import ir.lazydeveloper.jpodcatcher.model.Enclosure;
import ir.lazydeveloper.jpodcatcher.model.Image;
import ir.lazydeveloper.jpodcatcher.model.Item;
import ir.lazydeveloper.jpodcatcher.model.itunes.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class PodcastReaderSaxParserImpl implements PodcastReader {

    private static final Logger LOG = Logger.getLogger(PodcastReaderSaxParserImpl.class.getName());
    private final SAXParser saxParser;

    public PodcastReaderSaxParserImpl() {
        var factory = SAXParserFactory.newInstance();
        try {
            this.saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Trouble while initializing sax parser", e);
        }
    }

    @Override
    public Channel loadRSS(String uri) throws PodcastReaderException {
        try {
            var handler = new RSSHandler();
            saxParser.parse(uri, handler);
            Channel podcast = handler.getPodcast();
            if (podcast.title() == null || podcast.title().isEmpty()) {
                throw new PodcastReaderException("Not valid podcast");
            }
            return podcast;
        } catch (IOException | SAXException e) {
            throw new PodcastReaderException(e);
        }
    }

    private static class RSSHandler extends DefaultHandler {
        private final LinkedList<String> navigation = new LinkedList<>();

        private StringBuilder currentStringBuilder;
        private Channel.Builder channelBuilder;
        private Image.Builder imageBuilder;
        private Item.Builder itemBuilder;
        private ItunesChannelData.Builder itunesChannelDataBuilder;
        private ItunesCategory.Builder itunesCategoryBuilder;
        private ItunesOwner.Builder itunesOwnerBuilder;
        private ItunesItemData.Builder itunesItemDataBuilder;

        @Override
        public void startDocument() throws SAXException {
            channelBuilder = new Channel.Builder();
            imageBuilder = new Image.Builder();
            itemBuilder = new Item.Builder();
            itunesChannelDataBuilder = new ItunesChannelData.Builder();
            itunesCategoryBuilder = new ItunesCategory.Builder();
            itunesOwnerBuilder = new ItunesOwner.Builder();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            var supportedElement =
                    Element.getElement(localName.equals("") ? qName : localName + ":" + qName).orElse(null);
            Predicate<Element> checkParent = elm -> !navigation.isEmpty() && navigation.peek().equals(elm.getElementName());
            Predicate<Element> check2Parent = elm -> navigation.size() >= 2 && navigation.get(1).equals(elm.getElementName());

            if (supportedElement != null) {
                switch (supportedElement) {
                    case RSS -> {
                        if (!navigation.isEmpty()) {
                            throw new SAXException("No RSS element found in the XML");
                        }
                    }
                    case CHANNEL -> {
                        if (!checkParent.test(Element.RSS)) {
                            throw new SAXException("No RSS element found in the XML");
                        }
                    }
                    case IMAGE -> {
                        if (!checkParent.test(Element.CHANNEL)) {
                            throw new SAXException("No Channel element found in the XML");
                        }
                        imageBuilder = new Image.Builder();
                    }
                    case ITEM -> {
                        if (!checkParent.test(Element.CHANNEL)) {
                            throw new SAXException("No Channel element found in the XML");
                        }
                        itemBuilder = new Item.Builder();
                        itunesItemDataBuilder = new ItunesItemData.Builder();
                    }
                    case ENCLOSURE -> {
                        if (checkParent.test(Element.ITEM) && itemBuilder != null) {
                            readEnclosureElement(attributes);
                        }
                    }
                    case ITUNES_CATEGORY -> {
                        if (checkParent.test(Element.CHANNEL)) {
                            itunesCategoryBuilder.setCategory(attributes.getValue("text"));
                        } else if (checkParent.test(Element.ITUNES_CATEGORY) && check2Parent.test(Element.CHANNEL)) {
                            itunesCategoryBuilder.addSubCategory(attributes.getValue("text"));
                        }
                    }
                    case ITUNES_IMAGE -> {
                        if (checkParent.test(Element.CHANNEL)) {
                            itunesChannelDataBuilder.setImage(attributes.getValue("href"));
                        } else if (checkParent.test(Element.ITEM)) {
                            itunesItemDataBuilder.setImage(attributes.getValue("href"));
                        }
                    }
                    default -> currentStringBuilder = new StringBuilder();
                }

            } else {
                //TODO is logging enough?
                LOG.fine(() -> String.format("Element %s not supported yet", (qName)));
            }

            navigation.push(qName);
        }

        private void readEnclosureElement(Attributes attributes) {
            var enclosure = new Enclosure.Builder()
                    .setLength(Optional.ofNullable(attributes.getValue("length"))
                            .map(Long::valueOf).orElse(null))
                    .setType(attributes.getValue("type"))
                    .setUrl(attributes.getValue("url")).build();
            itemBuilder.setEnclosure(enclosure);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            navigation.poll();
            var element = Element.getElement(qName);
            if (!navigation.isEmpty() && element.isPresent()) {
                Predicate<Element> checkParent = elm -> elm.getElementName().equals(navigation.peek());
                Supplier<String> content = () -> currentStringBuilder == null ? null : currentStringBuilder.toString().trim();

                if (checkParent.test(Element.CHANNEL)) {
                    channelSwitches(element.get(), content.get());
                } else if (checkParent.test(Element.IMAGE)) {
                    imageSwitches(element.get(), content.get());
                } else if (checkParent.test(Element.ITEM)) {
                    itemSwitches(element.get(), content.get());
                } else if (checkParent.test(Element.ITUNES_OWNER)) {
                    itunesOwnerSwitches(element.get(), content.get());
                }
                if (Element.CHANNEL.getElementName().equals(navigation.peek())) {
                    channelBuilder.setItunesChannelData(itunesChannelDataBuilder.build());
                }
                currentStringBuilder = null;
            } else {
                //TODO decide
            }
        }

        private void itunesOwnerSwitches(Element element, String content) {
            switch (element) {
                case ITUNES_NAME -> itunesOwnerBuilder.setName(content);
                case ITUNES_EMAIL -> itunesOwnerBuilder.setEmail(content);
                default ->
                        LOG.warning(() -> String.format("%s element with value %s is not supported as itunes:owner info", element, content));
            }
        }

        private void itemSwitches(Element element, String content) {
            switch (element) {
                case GUID -> itemBuilder.setGuid(content);
                case TITLE -> itemBuilder.setTitle(content);
                case PUB_DATE -> itemBuilder.setPubDate(content);
                case LINK -> itemBuilder.setLink(content);
                case DESCRIPTION -> itemBuilder.setDescription(content);
                case CATEGORY -> itemBuilder.addCategory(content);
                case ENCLOSURE -> {/*Already handled with attributes*/}
                case ITUNES_EPISODE -> itunesItemDataBuilder.setEpisode(content);
                case ITUNES_SEASON -> itunesItemDataBuilder.setSeason(content);
                case ITUNES_EPISODE_TYPE -> itunesItemDataBuilder.setEpisodeType(content);
                case ITUNES_TITLE -> itunesItemDataBuilder.setTitle(content);
                case ITUNES_DURATION -> itunesItemDataBuilder.setDuration(content);
                case ITUNES_EXPLICIT -> itunesItemDataBuilder.setExplicit(content);
                case ITUNES_BLOCK -> itunesItemDataBuilder.setBlock(content);
                default ->
                        LOG.warning(() -> String.format("%s element with value %s is not supported as ITEM info", element, content));
            }
        }

        private void imageSwitches(Element element, String content) {
            switch (element) {
                case URL -> imageBuilder.setUrl(content);
                case TITLE -> imageBuilder.setTitle(content);
                case LINK -> imageBuilder.setLink(content);
                default ->
                        LOG.warning(() -> String.format("%s element with value %s is not supported as IMAGE info", element, content));
            }
        }

        private void channelSwitches(Element element, String content) {
            switch (element) {
                case TITLE -> channelBuilder.setTitle(content);
                case DESCRIPTION -> channelBuilder.setDescription(content);
                case LINK -> channelBuilder.setLink(content);
                case COPYRIGHT -> channelBuilder.setCopyright(content);
                case LANGUAGE -> channelBuilder.setLanguage(content);
                case GENERATOR -> channelBuilder.setGenerator(content);
                case PUB_DATE -> channelBuilder.setPubDate(content);
                case LAST_BUILD_DATE -> channelBuilder.setLastBuildDate(content);
                case IMAGE -> {
                    channelBuilder.setImage(imageBuilder.build());
                    imageBuilder = null;
                }
                case ITEM -> {
                    itemBuilder.setItunesItemData(itunesItemDataBuilder.build());
                    channelBuilder.addItem(itemBuilder.build());
                    itemBuilder = null;
                    itunesItemDataBuilder = null;
                }
                case ITUNES_CATEGORY -> itunesChannelDataBuilder.setCategory(itunesCategoryBuilder.build());
                case ITUNES_EXPLICIT -> itunesChannelDataBuilder.setExplicit(content);
                case ITUNES_AUTHOR -> itunesChannelDataBuilder.setAuthor(content);
                case ITUNES_OWNER -> itunesChannelDataBuilder.setOwner(itunesOwnerBuilder.build());
                case ITUNES_TITLE -> itunesChannelDataBuilder.setTitle(content);
                case ITUNES_TYPE -> itunesChannelDataBuilder.setType(content);
                case ITUNES_NEW_FEED_URL -> itunesChannelDataBuilder.setNewFeedUrl(content);
                case ITUNES_BLOCK -> itunesChannelDataBuilder.setBlock(content);
                case ITUNES_COMPLETE -> itunesChannelDataBuilder.setComplete(content);
                default ->
                        LOG.warning(() -> String.format("%s element with value %s is not supported as CHANNEL info", element, content));
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (currentStringBuilder != null) {
                currentStringBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            //Do Nothing
        }

        public Channel getPodcast() {
            return channelBuilder.build();
        }

        private enum Element {
            RSS, CHANNEL, TITLE, DESCRIPTION, LINK, PUB_DATE("pubDate"),
            LAST_BUILD_DATE("lastBuildDate"), LANGUAGE, COPYRIGHT, GENERATOR, IMAGE, ITEM,
            URL, GUID, AUTHOR, CATEGORY, ENCLOSURE,
            ITUNES_IMAGE("itunes:image"),
            ITUNES_CATEGORY("itunes:category"),
            ITUNES_EXPLICIT("itunes:explicit"),
            ITUNES_AUTHOR("itunes:author"),
            ITUNES_OWNER("itunes:owner"),
            ITUNES_NAME("itunes:name"),
            ITUNES_EMAIL("itunes:email"),
            ITUNES_TITLE("itunes:title"),
            ITUNES_TYPE("itunes:type"),
            ITUNES_NEW_FEED_URL("itunes:new-feed-url"),
            ITUNES_BLOCK("itunes:block"),
            ITUNES_COMPLETE("itunes:complete"),
            ITUNES_EPISODE("itunes:episode"),
            ITUNES_SEASON("itunes:season"),
            ITUNES_EPISODE_TYPE("itunes:episodeType"),
            ITUNES_DURATION("itunes:duration");

            private final String elementName;

            Element() {
                this.elementName = name().toLowerCase();
            }

            Element(String elementName) {
                this.elementName = elementName;
            }

            static Optional<Element> getElement(String elementName) {
                return Arrays.stream(Element.values())
                        .filter(el -> el.elementName.equalsIgnoreCase(elementName)).findFirst();
            }

            String getElementName() {
                return elementName;
            }

        }
    }
}
