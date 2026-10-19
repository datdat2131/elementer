package com.astrax.elementer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra khai báo quyền trong plugin.yml (mỗi lệnh/chức năng một permission riêng, quyền admin mặc định op). */
class PluginYmlTest {
    private static YamlConfiguration plugin() throws Exception {
        try (Reader r = new InputStreamReader(PluginYmlTest.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        }
    }

    @Test
    void everyCommandPermissionIsDeclared() throws Exception {
        ConfigurationSection perms = plugin().getConfigurationSection("permissions");
        assertNotNull(perms);
        for (String p : List.of("main", "info", "skills", "stats", "top", "cast", "select", "awaken", "unlock", "switch", "aura", "wings", "exchange")) {
            assertNotNull(perms.getConfigurationSection("elementer.command." + p), p);
            assertEquals("true", perms.getString("elementer.command." + p + ".default"));
        }
        for (String p : List.of("reload", "give", "set", "reset", "boss")) {
            assertNotNull(perms.getConfigurationSection("elementer.admin." + p), p);
            assertEquals("op", perms.getString("elementer.admin." + p + ".default"));
        }
    }

    @Test
    void voucherPermissionDeclared() throws Exception {
        assertEquals("true", plugin().getString("permissions.elementer.voucher.use.default"));
    }

    @Test
    void adminWildcardContainsAllAdminNodes() throws Exception {
        ConfigurationSection children = plugin().getConfigurationSection("permissions.elementer.admin.*.children");
        assertNotNull(children);
        assertTrue(children.getKeys(true).containsAll(List.of("elementer.admin.reload", "elementer.admin.give",
                "elementer.admin.set", "elementer.admin.reset", "elementer.admin.boss")));
    }

    @Test
    void commandsAndMainClassDeclared() throws Exception {
        YamlConfiguration y = plugin();
        assertEquals("com.astrax.elementer.ElementerCore", y.getString("main"));
        assertTrue(y.contains("commands.elementer"));
        assertTrue(y.contains("commands.elementeradmin"));
    }
}
