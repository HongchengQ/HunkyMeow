package emu.grasscutter.database;

import dev.morphia.annotations.*;

@Entity(value = "counters", useDiscriminator = false)
public class DatabaseCounter {
    @Id private String id;
    private int count;

    public DatabaseCounter() {}

    public DatabaseCounter(String id) {
        this.id = id;
        this.count = 10000;
    }

    public int getNextId() {
        return ++count;
    }
}
