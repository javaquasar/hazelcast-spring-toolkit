package io.github.javaquasar.hazelcast.toolkit.example.boot3.model;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class ExampleBookCacheEntryCompactSerializer implements CompactSerializer<ExampleBookCacheEntry> {

    @Override
    public void write(CompactWriter writer, ExampleBookCacheEntry object) {
        writer.writeString("id", object.getId());
        writer.writeString("title", object.getTitle());
        writer.writeString("author", object.getAuthor());
        writer.writeString("isbn", object.getIsbn());
        writer.writeString("genre", object.getGenre());

        ExampleBookCacheEntry.PublisherInfo publisher = object.getPublisher();
        writer.writeString("publisherName", publisher != null ? publisher.getName() : null);
        writer.writeString("publisherCity", publisher != null ? publisher.getCity() : null);

        ExampleBookCacheEntry.InventorySnapshot inventory = object.getInventory();
        writer.writeString("inventoryShelfCode", inventory != null ? inventory.getShelfCode() : null);
        writer.writeInt32("inventoryCopiesAvailable", inventory != null ? nvl(inventory.getCopiesAvailable()) : 0);
        writer.writeBoolean("inventoryFeatured", inventory != null && Boolean.TRUE.equals(inventory.getFeatured()));
        writer.writeArrayOfString("inventoryTags", inventory != null ? inventory.getTags() : new String[0]);
    }

    @Override
    public ExampleBookCacheEntry read(CompactReader reader) {
        ExampleBookCacheEntry.PublisherInfo publisher = new ExampleBookCacheEntry.PublisherInfo(
                reader.readString("publisherName"),
                reader.readString("publisherCity")
        );

        ExampleBookCacheEntry.InventorySnapshot inventory = new ExampleBookCacheEntry.InventorySnapshot(
                reader.readString("inventoryShelfCode"),
                reader.readInt32("inventoryCopiesAvailable"),
                reader.readBoolean("inventoryFeatured"),
                reader.readArrayOfString("inventoryTags")
        );

        return new ExampleBookCacheEntry(
                reader.readString("id"),
                reader.readString("title"),
                reader.readString("author"),
                reader.readString("isbn"),
                reader.readString("genre"),
                publisher,
                inventory
        );
    }

    @Override
    public String getTypeName() {
        return "ExampleBookCacheEntry";
    }

    @Override
    public Class<ExampleBookCacheEntry> getCompactClass() {
        return ExampleBookCacheEntry.class;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
