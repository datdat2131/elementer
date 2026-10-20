package com.astrax.elementer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUpdatesTest {
    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void mergeAddsOnlyMissingKeysAndKeepsUserValues() throws Exception {
        YamlConfiguration user = yaml("a: mine\ngui:\n  title: old\n");
        YamlConfiguration def = yaml("a: default\nb: new\ngui:\n  title: default\n  title-exchange: Doi\n  lore:\n    need: x\nlist:\n  - 1\n  - 2\n");
        int added = ConfigUpdates.mergeMissing(user, def);
        assertEquals(4, added);
        assertEquals("mine", user.getString("a"));
        assertEquals("old", user.getString("gui.title"));
        assertEquals("new", user.getString("b"));
        assertEquals("Doi", user.getString("gui.title-exchange"));
        assertEquals("x", user.getString("gui.lore.need"));
        assertEquals(List.of(1, 2), user.getIntegerList("list"));
    }

    @Test
    void mergeIsIdempotent() throws Exception {
        YamlConfiguration user = yaml("a: 1\n");
        YamlConfiguration def = yaml("a: 2\nb: 3\n");
        assertEquals(1, ConfigUpdates.mergeMissing(user, def));
        assertEquals(0, ConfigUpdates.mergeMissing(user, def));
    }

    @Test
    void missingChildrenListsNewElementIds() throws Exception {
        YamlConfiguration user = yaml("elements:\n  FIRE:\n    name: x\n  ICE:\n    name: y\n");
        YamlConfiguration def = yaml("elements:\n  FIRE:\n    name: x\n  ICE:\n    name: y\n  DARK:\n    name: z\n  DRAGON:\n    name: w\n");
        assertEquals(List.of("DARK", "DRAGON"), ConfigUpdates.missingChildren(user, def, "elements"));
        assertTrue(ConfigUpdates.missingChildren(def, def, "elements").isEmpty());
        assertTrue(ConfigUpdates.missingChildren(user, def, "nope").isEmpty());
    }

    @Test
    void missingChildrenWhenSectionAbsentInUserFile() throws Exception {
        YamlConfiguration user = yaml("x: 1\n");
        YamlConfiguration def = yaml("bosses:\n  fire_lord:\n    name: a\n");
        assertEquals(List.of("fire_lord"), ConfigUpdates.missingChildren(user, def, "bosses"));
    }
}
