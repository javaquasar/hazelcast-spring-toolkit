package io.github.javaquasar.hazelcast.toolkit.example.boot3.model;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = ExampleBookCacheEntryCompactSerializer.class)
public class ExampleBookCacheEntry {

    private String id;
    private String title;
    private String author;
    private String isbn;
    private String genre;
    private PublisherInfo publisher;
    private InventorySnapshot inventory;

    protected ExampleBookCacheEntry() {
    }

    public ExampleBookCacheEntry(
            String id,
            String title,
            String author,
            String isbn,
            String genre,
            PublisherInfo publisher,
            InventorySnapshot inventory) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.genre = genre;
        this.publisher = publisher;
        this.inventory = inventory;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getGenre() {
        return genre;
    }

    public PublisherInfo getPublisher() {
        return publisher;
    }

    public InventorySnapshot getInventory() {
        return inventory;
    }

    @Override
    public String toString() {
        return "ExampleBookCacheEntry{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", isbn='" + isbn + '\'' +
                ", genre='" + genre + '\'' +
                ", publisher=" + publisher +
                ", inventory=" + inventory +
                '}';
    }

    public static class PublisherInfo {

        private String name;
        private String city;

        protected PublisherInfo() {
        }

        public PublisherInfo(String name, String city) {
            this.name = name;
            this.city = city;
        }

        public String getName() {
            return name;
        }

        public String getCity() {
            return city;
        }

        @Override
        public String toString() {
            return "PublisherInfo{" +
                    "name='" + name + '\'' +
                    ", city='" + city + '\'' +
                    '}';
        }
    }

    public static class InventorySnapshot {

        private String shelfCode;
        private Integer copiesAvailable;
        private Boolean featured;
        private String[] tags;

        protected InventorySnapshot() {
        }

        public InventorySnapshot(String shelfCode, Integer copiesAvailable, Boolean featured, String[] tags) {
            this.shelfCode = shelfCode;
            this.copiesAvailable = copiesAvailable;
            this.featured = featured;
            this.tags = tags;
        }

        public String getShelfCode() {
            return shelfCode;
        }

        public Integer getCopiesAvailable() {
            return copiesAvailable;
        }

        public Boolean getFeatured() {
            return featured;
        }

        public String[] getTags() {
            return tags;
        }

        @Override
        public String toString() {
            return "InventorySnapshot{" +
                    "shelfCode='" + shelfCode + '\'' +
                    ", copiesAvailable=" + copiesAvailable +
                    ", featured=" + featured +
                    '}';
        }
    }
}
