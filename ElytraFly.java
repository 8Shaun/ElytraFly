package elytrafly;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class ElytraFlyPlugin extends JavaPlugin implements Listener {
    private final Set<Player> flyingPlayers = new HashSet<>();
    private final Random random = new Random();
    private boolean consumeItem;
    private Material itemToConsume;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("efly").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                toggleElytraFlight(player);
            } else {
                sender.sendMessage(ChatColor.RED + "此指令僅限玩家使用！");
            }
            return true;
        });

        saveDefaultConfig();
        reloadConfigSettings();
    }

    @Override
    public void onDisable() {
        for (Player player : flyingPlayers) {
            disableElytraFlight(player);
        }
        flyingPlayers.clear();
    }

    private void reloadConfigSettings() {
        consumeItem = getConfig().getBoolean("consume_item", true);
        String itemName = getConfig().getString("item_to_consume", "FIREWORK_ROCKET");
        itemToConsume = Material.matchMaterial(itemName);
        if (itemToConsume == null) {
            getLogger().warning("無效的物品名稱: " + itemName + "，預設為 FIREWORK_ROCKET");
            itemToConsume = Material.FIREWORK_ROCKET;
        }
    }

    private void toggleElytraFlight(Player player) {
        if (!player.hasPermission("efly.use")) {
            player.sendMessage(ChatColor.RED + "你沒有權限使用此功能！");
            return;
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
            player.sendMessage(ChatColor.RED + "你需要穿戴鞘翅才能啟動飛行模式！");
            return;
        }

        if (flyingPlayers.contains(player)) {
            disableElytraFlight(player);
            player.sendMessage(ChatColor.YELLOW + "飛行模式已關閉！");
        } else {
            enableElytraFlight(player);
            player.sendMessage(ChatColor.GREEN + "飛行模式已開啟！");
        }
    }

    private void enableElytraFlight(Player player) {
        flyingPlayers.add(player);
        player.setAllowFlight(true);
        startDurabilityTask(player);
    }

    private void disableElytraFlight(Player player) {
        flyingPlayers.remove(player);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void startDurabilityTask(Player player) {
        int durabilityLossRate = getConfig().getInt("durability_loss_ticks", 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!flyingPlayers.contains(player) || !player.isOnline()) {
                    cancel();
                    return;
                }

                ItemStack chestplate = player.getInventory().getChestplate();
                if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
                    disableElytraFlight(player);
                    player.sendMessage(ChatColor.RED + "你的鞘翅不見了，飛行模式關閉！");
                    cancel();
                    return;
                }

                if (!player.isFlying()) return;

                // 扣除耐久度
                short durability = chestplate.getDurability();
                int maxDurability = chestplate.getType().getMaxDurability();
                int unbreakingLevel = chestplate.getEnchantmentLevel(Enchantment.UNBREAKING);

                if (durability >= maxDurability - 1) {
                    disableElytraFlight(player);
                    player.sendMessage(ChatColor.RED + "你的鞘翅已損壞，飛行模式關閉！");
                    cancel();
                    return;
                }

                if (unbreakingLevel == 0 || random.nextInt(unbreakingLevel + 1) == 0) {
                    chestplate.setDurability((short) (durability + 1));
                }

                // 消耗物品
                if (consumeItem) {
                    ItemStack requiredItem = new ItemStack(itemToConsume, 1);
                    if (!player.getInventory().containsAtLeast(requiredItem, 1)) {
                        disableElytraFlight(player);
                        player.sendMessage(ChatColor.RED + "你的 " + itemToConsume.name() + " 不足，飛行模式關閉！");
                        cancel();
                        return;
                    }
                    player.getInventory().removeItem(requiredItem);
                }
            }
        }.runTaskTimer(this, 0, durabilityLossRate);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        disableElytraFlight(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getSlot() == 38 && flyingPlayers.contains(player)) { // 胸甲槽 (38)
            disableElytraFlight(player);
            player.sendMessage(ChatColor.YELLOW + "你變更了胸甲，飛行模式已關閉！");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (flyingPlayers.contains(player)) {
                event.setCancelled(false); // 確保墜落傷害正常運作
            }
        }
    }
}
