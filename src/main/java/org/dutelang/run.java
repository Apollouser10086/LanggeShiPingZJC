package org.dutelang;

import github.saukiya.sxattribute.SXAttribute;
import github.saukiya.sxattribute.api.SXAttributeAPI;
import github.saukiya.sxattribute.data.attribute.SXAttributeData;
import github.saukiya.sxattribute.data.attribute.SubAttribute;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.langgeshipingapi.Main;
import org.langgeshipingapi.event.PlayerAccessoryStatsUpdateEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class run extends JavaPlugin implements Listener {

    private final Map<UUID, Map<String, Double>> playerStatsCache = new ConcurrentHashMap<>();
    private SXAttributeAPI sxAPI;
    private Main accessoryAPI;
    private SXAttribute sxPlugin;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("SX-Attribute")) {
            getLogger().severe("§c 未找到 SX-Attribute，插件无法运行！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            sxPlugin = (SXAttribute) Bukkit.getPluginManager().getPlugin("SX-Attribute");
            sxAPI = sxPlugin.getApi();
            getLogger().info("§a✓ SXAttribute API 已成功加载");
        } catch (Exception e) {
            getLogger().severe("§c 无法获取 SXAttribute API: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("LangGeShiPingAPI");
        if (plugin == null || !(plugin instanceof Main) || !plugin.isEnabled()) {
            getLogger().severe("§c 未找到 LangGeShiPingAPI，插件无法运行！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        accessoryAPI = (Main) plugin;
        getLogger().info("§a✓ LangGeShiPingAPI 已成功连接");

        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        getLogger().info("§a✓ LangGeAttribute 已启用");
        getLogger().info("§7 外部依赖检测：SXAttribute ✓ | LangGeShiPingAPI ✓");

    }

    @Override
    public void onDisable() {
        for (UUID uuid : playerStatsCache.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                clearPlayerAttributes(player);
            }
        }
        playerStatsCache.clear();
        getLogger().info("§c LangGeAttribute 已停止");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAccessoryStatsUpdate(PlayerAccessoryStatsUpdateEvent event) {
        Player player = event.getPlayer();
        Map<String, Double> stats = event.getStats();

        if (stats == null || stats.isEmpty()) {
            return;
        }

        playerStatsCache.put(player.getUniqueId(), stats);
        getLogger().info("§7收到饰品属性更新事件，开始应用...");
        applyAttributes(player, stats);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            if (accessoryAPI == null || !accessoryAPI.isAvailable()) {
                getLogger().warning("§c LangGeShiPingAPI 不可用");
                return;
            }
            try {
                Map<String, Double> stats = accessoryAPI.calculatePlayerStats(player);
                if (stats == null || stats.isEmpty()) return;
                playerStatsCache.put(player.getUniqueId(), stats);
                getLogger().info("§7玩家登录，加载饰品属性并应用...");
                applyAttributes(player, stats);
            } catch (Exception e) {
                getLogger().warning("§c 获取玩家属性失败：" + e.getMessage());
                e.printStackTrace();
            }
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearPlayerAttributes(player);
        playerStatsCache.remove(player.getUniqueId());
    }

    private void applyAttributes(Player player, Map<String, Double> stats) {
        try {
            // 1. 清除旧数据
            clearPlayerAttributes(player);

            // 2. 获取或创建本插件的数据容器
            SXAttributeData playerData = sxAPI.getEntityAPIData(getClass(), player.getUniqueId());
            if (playerData == null) {
                playerData = new SXAttributeData();
            }

            // 3. 设置属性（根据 SX-Attribute 配置调整）

            // 攻击力 Damage
            double attackMin = stats.getOrDefault("attack_min", 0.0);
            double attackMax = stats.getOrDefault("attack_max", 0.0);
            setAttributeMulti(playerData, "Damage", new double[]{attackMin, attackMax});

            // PVP 攻击力
            if (stats.containsKey("pvp_damage")) {
                setAttributeSingle(playerData, "PVPDamage", stats.get("pvp_damage"));
            }

            // PVE 攻击力
            if (stats.containsKey("pve_damage")) {
                setAttributeSingle(playerData, "PVEDamage", stats.get("pve_damage"));
            }

            // 暴击 Crit (几率、伤害)
            double critRate = stats.getOrDefault("crit_rate", 0.0) * 100;
            double critDamage = stats.getOrDefault("crit_damage", 0.0) * 100;
            setAttributeMulti(playerData, "Crit", new double[]{critRate, critDamage});

            // 暴伤抵抗 CritDefense
            double critResist = stats.getOrDefault("crit_resist", 0.0) * 100;
            setAttributeSingle(playerData, "CritDefense", critResist);

            // 生命上限 Health
            double health = stats.getOrDefault("health", 0.0);
            setAttributeSingle(playerData, "Health", health);

            // 生命加成 health_boost (放入 Health 的第二个值)
            double healthBoost = stats.getOrDefault("health_boost", 0.0) * 100;
            if (healthBoost != 0) {
                SubAttribute healthAttr = playerData.getSubAttribute("Health");
                if (healthAttr != null && healthAttr.getAttributes().length >= 2) {
                    healthAttr.getAttributes()[1] = healthBoost;
                }
            }

            // 防御力 Defense
            if (stats.containsKey("defense")) {
                setAttributeSingle(playerData, "Defense", stats.get("defense"));
            }

            // PVP 防御力
            if (stats.containsKey("pvp_defense")) {
                setAttributeSingle(playerData, "PVPDefense", stats.get("pvp_defense"));
            }

            // PVE 防御力
            if (stats.containsKey("pve_defense")) {
                setAttributeSingle(playerData, "PVEDefense", stats.get("pve_defense"));
            }

            // 防御加成 defense_boost (如果 SX-Attribute 有 DefBoost 属性)
            double defenseBoost = stats.getOrDefault("defense_boost", 0.0) * 100;
            if (defenseBoost != 0) {
                setAttributeSingle(playerData, "DefBoost", defenseBoost);
            }

            // 韧性 Toughness (伤害加成)
            double toughness = stats.getOrDefault("damage_boost", 0.0) * 100;
            setAttributeSingle(playerData, "Toughness", toughness);

            // 生命恢复 HealthRegen
            setAttributeSingle(playerData, "HealthRegen", stats.getOrDefault("health_regen", 0.0));

            // 吸血 LifeSteal
            double lifeSteal = stats.getOrDefault("lifesteal", 0.0) * 100;
            setAttributeSingle(playerData, "LifeSteal", lifeSteal);

            // 吸血防御
            if (stats.containsKey("lifesteal_defense")) {
                setAttributeSingle(playerData, "LifeStealDefense", stats.get("lifesteal_defense") * 100);
            }

            // 闪避 Dodge
            if (stats.containsKey("dodge")) {
                setAttributeSingle(playerData, "Dodge", stats.get("dodge") * 100);
            }

            // 命中 HitRate
            if (stats.containsKey("hit_rate")) {
                setAttributeSingle(playerData, "HitRate", stats.get("hit_rate") * 100);
            }

            // 破甲 Real
            if (stats.containsKey("real")) {
                setAttributeSingle(playerData, "Real", stats.get("real") * 100);
            }

            // 反射 Reflection
            if (stats.containsKey("reflection_rate")) {
                setAttributeSingle(playerData, "ReflectionRate", stats.get("reflection_rate") * 100);
            }
            if (stats.containsKey("reflection")) {
                setAttributeSingle(playerData, "Reflection", stats.get("reflection") * 100);
            }

            // 格挡 Block
            if (stats.containsKey("block_rate")) {
                setAttributeSingle(playerData, "BlockRate", stats.get("block_rate") * 100);
            }
            if (stats.containsKey("block")) {
                setAttributeSingle(playerData, "Block", stats.get("block") * 100);
            }

            // 异常状态几率
            if (stats.containsKey("ignition")) {
                setAttributeSingle(playerData, "Ignition", stats.get("ignition") * 100);
            }
            if (stats.containsKey("wither")) {
                setAttributeSingle(playerData, "Wither", stats.get("wither") * 100);
            }
            if (stats.containsKey("poison")) {
                setAttributeSingle(playerData, "Poison", stats.get("poison") * 100);
            }
            if (stats.containsKey("blindness")) {
                setAttributeSingle(playerData, "Blindness", stats.get("blindness") * 100);
            }
            if (stats.containsKey("slowness")) {
                setAttributeSingle(playerData, "Slowness", stats.get("slowness") * 100);
            }
            if (stats.containsKey("lightning")) {
                setAttributeSingle(playerData, "Lightning", stats.get("lightning") * 100);
            }
            if (stats.containsKey("tearing")) {
                setAttributeSingle(playerData, "Tearing", stats.get("tearing") * 100);
            }

            // 荆棘抵抗
            if (stats.containsKey("thorns_resist")) {
                setAttributeSingle(playerData, "ThornsResist", stats.get("thorns_resist") * 100);
            }

            // 掉落倍率
            if (stats.containsKey("mythicmobs_drop")) {
                setAttributeSingle(playerData, "MythicmobsDrop", stats.get("mythicmobs_drop") * 100);
            }

            // 经验加成
            if (stats.containsKey("exp_addition")) {
                setAttributeSingle(playerData, "ExpAddition", stats.get("exp_addition") * 100);
            }

            // 移动速度
            if (stats.containsKey("speed")) {
                setAttributeSingle(playerData, "Speed", stats.get("speed") * 100);
            }

            // 5. 标记数据有效
            setDataValid(playerData);

            // 6. 保存并更新
            sxAPI.setEntityAPIData(getClass(), player.getUniqueId(), playerData);
            sxAPI.updateStats(player);

            getLogger().info("§a✓ 成功应用 " + stats.size() + " 条属性到玩家 " + player.getName());

        } catch (Exception e) {
            getLogger().severe("§c 应用属性时发生错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearPlayerAttributes(Player player) {
        try {
            sxAPI.removeEntityAPIData(getClass(), player.getUniqueId());
            sxAPI.updateStats(player);
        } catch (Exception ignored) {}
    }

    /** 设置单值属性（只改第一个元素） */
    private void setAttributeSingle(SXAttributeData data, String name, double value) {
        if (value == 0) return;
        SubAttribute attr = data.getSubAttribute(name);
        if (attr == null) {
            return;
        }
        double[] target = attr.getAttributes();
        if (target.length > 0) {
            target[0] = value;
        }
    }

    /** 设置多值属性（从索引0开始依次设置） */
    private void setAttributeMulti(SXAttributeData data, String name, double[] values) {
        if (values == null || values.length == 0) return;
        SubAttribute attr = data.getSubAttribute(name);
        if (attr == null) {

            return;
        }
        double[] target = attr.getAttributes();
        for (int i = 0; i < Math.min(target.length, values.length); i++) {
            target[i] = values[i];
        }
    }

    /** 标记 SXAttributeData 为有效 */
    private boolean setDataValid(SXAttributeData data) {
        try {
            Field validField = data.getClass().getDeclaredField("valid");
            validField.setAccessible(true);
            validField.setBoolean(data, true);
            return true;
        } catch (Exception e) {
            try {
                Method validMethod = data.getClass().getDeclaredMethod("valid");
                validMethod.setAccessible(true);
                validMethod.invoke(data);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}