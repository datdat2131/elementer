package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/** Lớp chính: khởi tạo cấu hình, lưu trữ, các service, listener và lệnh. */
public final class ElementerCore extends JavaPlugin {
    private ElementConfig elementConfig;
    private ElementStorage storage;
    private ElementService elementService;
    private SkillService skillService;
    private CombatService combatService;
    private ElementGUI gui;
    private AuraService auraService;
    private VoucherService voucherService;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Không tạo được thư mục dữ liệu.");
        }
        elementConfig = new ElementConfig(this);
        elementConfig.load();

        String type = getConfig().getString("database.type", "sqlite");
        if (!type.equalsIgnoreCase("sqlite")) {
            getLogger().warning("database.type='" + type + "' chưa được hỗ trợ ở bản 1.0, dùng SQLite.");
        }
        File dbFile = new File(getDataFolder(), getConfig().getString("database.sqlite-file", "data.db"));
        storage = new ElementStorage("jdbc:sqlite:" + dbFile.getAbsolutePath(), getLogger());
        try {
            storage.init().join();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Không khởi tạo được cơ sở dữ liệu - tắt plugin.", e);
            storage.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        combatService = new CombatService(elementConfig);
        elementService = new ElementService(this, elementConfig, storage, combatService);
        skillService = new SkillService(this, elementConfig, elementService, combatService);
        combatService.bind(elementService);
        elementService.bind(skillService);
        auraService = new AuraService(this, elementConfig, elementService);
        OwnershipActions actions = new OwnershipActions(elementConfig, elementService);
        gui = new ElementGUI(this, elementConfig, elementService, skillService, actions, auraService);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, elementConfig, elementService, skillService, combatService), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(elementConfig, elementService, combatService), this);
        Bukkit.getPluginManager().registerEvents(new ProgressListener(this, elementConfig, elementService), this);
        Bukkit.getPluginManager().registerEvents(gui, this);
        voucherService = new VoucherService(this, elementConfig, elementService, storage);
        Bukkit.getPluginManager().registerEvents(new VoucherListener(voucherService, elementConfig), this);

        PluginCommand main = getCommand("elementer");
        PluginCommand admin = getCommand("elementeradmin");
        if (main != null) {
            ElementerCommand c = new ElementerCommand(elementConfig, elementService, skillService, gui, actions, auraService);
            main.setExecutor(c);
            main.setTabCompleter(c);
        }
        if (admin != null) {
            ElementerAdminCommand c = new ElementerAdminCommand(this, elementConfig, elementService, voucherService);
            admin.setExecutor(c);
            admin.setTabCompleter(c);
        }

        auraService.start();
        elementService.start();
        // Hỗ trợ /reload hoặc plugin được bật khi đã có người chơi online
        for (Player p : Bukkit.getOnlinePlayers()) elementService.load(p);
        getLogger().info("AstraxElementer " + getPluginMeta().getVersion() + " đã bật.");
    }

    @Override
    public void onDisable() {
        if (auraService != null) auraService.stop();
        if (elementService != null) elementService.shutdown();
        if (storage != null) storage.close();
    }

    /** Tải lại mọi file cấu hình. */
    public void reloadAll() {
        elementConfig.load();
        elementService.reapplyAll();
        auraService.start(); // áp dụng lại aura.interval-ticks
    }
}
