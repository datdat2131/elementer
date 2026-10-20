package com.astrax.elementer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý file YAML cũ sau khi cập nhật plugin (plugin không tự ghi đè file đã có):
 * tin nhắn thiếu được bù từ bản mặc định trong JAR; mục mới (nguyên tố/kỹ năng/boss) thiếu thì cảnh báo;
 * lệnh /elementeradmin updateconfig sao lưu rồi ghi đè bằng bản mới.
 */
public final class ConfigUpdates {
    private static final List<String> REPLACEABLE = List.of("elements.yml", "skills.yml", "bosses.yml", "messages.yml");

    private ConfigUpdates() {}

    /** Đọc bản YAML mặc định đi kèm trong JAR (rỗng nếu không có). */
    public static YamlConfiguration bundled(JavaPlugin plugin, String file) {
        InputStream in = plugin.getResource(file);
        if (in == null) return new YamlConfiguration();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    /**
     * Thêm vào {@code user} (chỉ trong bộ nhớ, không ghi file) mọi khóa còn thiếu so với bản mặc định.
     * @return số khóa đã thêm
     */
    public static int mergeMissing(YamlConfiguration user, YamlConfiguration defaults) {
        int added = 0;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) continue;
            if (!user.contains(path, true)) {
                user.set(path, defaults.get(path));
                added++;
            }
        }
        return added;
    }

    /** Tên các mục con của {@code section} có trong bản mặc định nhưng thiếu trong file người dùng. */
    public static List<String> missingChildren(YamlConfiguration user, YamlConfiguration defaults, String section) {
        List<String> out = new ArrayList<>();
        var def = defaults.getConfigurationSection(section);
        if (def == null) return out;
        var mine = user.getConfigurationSection(section);
        for (String id : def.getKeys(false)) {
            if (mine == null || !mine.contains(id, true)) out.add(id);
        }
        return out;
    }

    public static void warnMissing(JavaPlugin plugin, String file, YamlConfiguration user, String section) {
        List<String> missing = missingChildren(user, bundled(plugin, file), section);
        if (!missing.isEmpty()) {
            plugin.getLogger().warning(file + " của bạn thiếu các mục mới có trong bản cập nhật: " + missing
                    + ". Chạy /elementeradmin updateconfig (tự sao lưu file cũ) hoặc xóa file để plugin tạo lại.");
        }
    }

    /**
     * Sao lưu elements/skills/bosses/messages.yml vào thư mục backup-thời-gian rồi ghi đè bằng bản mới trong JAR.
     * config.yml được giữ nguyên.
     * @return tên thư mục sao lưu, hoặc null nếu thất bại
     */
    public static String backupAndReplace(JavaPlugin plugin) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = plugin.getDataFolder().toPath().resolve("backup-" + stamp);
        try {
            Files.createDirectories(dir);
            for (String f : REPLACEABLE) {
                Path p = plugin.getDataFolder().toPath().resolve(f);
                if (Files.exists(p)) Files.copy(p, dir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
                plugin.saveResource(f, true);
            }
            return dir.getFileName().toString();
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Cập nhật cấu hình thất bại: " + e.getMessage());
            return null;
        }
    }
}
