package me.custom.huntersevent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class HuntersEvent extends JavaPlugin implements Listener, CommandExecutor {

    private UUID runnerId = null;
    private final Set<UUID> hunters = new HashSet<>();
    private final Map<UUID, Integer> totemUses = new HashMap<>();
    private boolean eventRunning = false;
    private int elapsedSeconds = 0;
    private int stage = 1;
    private BukkitTask eventTask = null;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(this);
        }
        getLogger().info("100 Hunters vs 1 PvPer Event Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        stopEvent();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("event")) return false;
        if (!sender.hasPermission("huntersevent.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /event <runner|hunter|start|stop>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "runner" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /event runner <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                runnerId = target.getUniqueId();
                hunters.remove(runnerId);
                sender.sendMessage(Component.text(target.getName() + " is now the Runner!", NamedTextColor.GOLD));
            }
            case "hunter" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /event hunter <player|all>", NamedTextColor.RED));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.getUniqueId().equals(runnerId)) {
                            hunters.add(p.getUniqueId());
                        }
                    }
                    sender.sendMessage(Component.text("All other online players added as Hunters!", NamedTextColor.GREEN));
                } else {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                        return true;
                    }
                    if (target.getUniqueId().equals(runnerId)) runnerId = null;
                    hunters.add(target.getUniqueId());
                    sender.sendMessage(Component.text(target.getName() + " added as a Hunter!", NamedTextColor.GREEN));
                }
            }
            case "start" -> {
                if (runnerId == null || Bukkit.getPlayer(runnerId) == null) {
                    sender.sendMessage(Component.text("Set an online Runner first with /event runner <player>!", NamedTextColor.RED));
                    return true;
                }
                if (eventRunning) {
                    sender.sendMessage(Component.text("Event is already running!", NamedTextColor.RED));
                    return true;
                }
                startEvent();
            }
            case "stop" -> {
                stopEvent();
                Bukkit.broadcast(Component.text("The 100 Hunters Event has been stopped!", NamedTextColor.RED));
            }
            default -> sender.sendMessage(Component.text("Usage: /event <runner|hunter|start|stop>", NamedTextColor.YELLOW));
        }
        return true;
    }
    private void startEvent() {
        eventRunning = true;
        elapsedSeconds = 0;
        stage = 1;
        totemUses.clear();

        Player runner = Bukkit.getPlayer(runnerId);
        if (runner != null) {
            giveRunnerKit(runner);
            runner.showTitle(Title.title(
                Component.text("SURVIVE THE HUNT!", NamedTextColor.GOLD),
                Component.text("100 Hunters are coming for you!", NamedTextColor.YELLOW)
            ));
        }

        for (UUID hunterId : hunters) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null) {
                giveHunterKit(hunter);
                hunter.showTitle(Title.title(
                    Component.text("HUNT THE RUNNER!", NamedTextColor.RED),
                    Component.text("Use your compass to track them!", NamedTextColor.GRAY)
                ));
            }
        }

        eventTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventRunning) {
                    cancel();
                    return;
                }
                elapsedSeconds++;

                if (elapsedSeconds == 300) {
                    stage = 2;
                    Bukkit.broadcast(Component.text("STAGE 2: Hunters gain Speed I & Supply Drop incoming!", NamedTextColor.RED));
                    spawnSupplyDrop();
                } else if (elapsedSeconds == 600) {
                    stage = 3;
                    Bukkit.broadcast(Component.text("STAGE 3: Final Showdown! Final Supply Drop spawned!", NamedTextColor.DARK_RED));
                    spawnSupplyDrop();
                }

                updateScoreboards();
                updateCompasses();
            }
        }.runTaskTimer(this, 20L, 20L);

        Bukkit.broadcast(Component.text("The 100 Hunters vs 1 PvPer Event has STARTED!", NamedTextColor.GREEN));
    }

    private void stopEvent() {
        eventRunning = false;
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    private void giveRunnerKit(Player p) {
        p.getInventory().clear();
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        p.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        p.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        p.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 8));
        p.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 32));
    }

    private void giveHunterKit(Player p) {
        p.getInventory().clear();
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        p.getInventory().addItem(new ItemStack(Material.BOW));
        p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16));
        p.getInventory().addItem(createTrackerCompass());
        if (stage >= 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
    }

    private ItemStack createTrackerCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Runner Tracker", NamedTextColor.RED));
            compass.setItemMeta(meta);
        }
        return compass;
    }

    private void updateCompasses() {
        Player runner = runnerId != null ? Bukkit.getPlayer(runnerId) : null;
        if (runner == null) return;
        for (UUID hunterId : hunters) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.getWorld().equals(runner.getWorld())) {
                hunter.setCompassTarget(runner.getLocation());
            }
        }
    }

    private void spawnSupplyDrop() {
        Player runner = runnerId != null ? Bukkit.getPlayer(runnerId) : null;
        if (runner == null) return;
        Location loc = runner.getLocation().clone().add(20, 0, 20);
        loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            inv.addItem(new ItemStack(Material.ENDER_PEARL, 4));
        }
        Bukkit.broadcast(Component.text("Supply Drop spawned at X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ(), NamedTextColor.AQUA));
    }

    private void updateScoreboards() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("huntersevent", Criteria.DUMMY, Component.text("100 HUNTERS EVENT", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int mins = elapsedSeconds / 60;
        int secs = elapsedSeconds % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);

        obj.getScore("Stage: " + stage).setScore(4);
        obj.getScore("Time: " + timeStr).setScore(3);
        obj.getScore("Hunters: " + hunters.size()).setScore(2);
        obj.getScore("Runner: " + (runnerId != null && Bukkit.getPlayer(runnerId) != null ? Bukkit.getPlayer(runnerId).getName() : "None")).setScore(1);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    @EventHandler
    public void onTotemUse(EntityResurrectEvent event) {
        if (!eventRunning || !(event.getEntity() instanceof Player player)) return;
        int used = totemUses.getOrDefault(player.getUniqueId(), 0);
        if (used >= 3) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You have reached the maximum limit of 3 Totems!", NamedTextColor.RED));
        } else {
            totemUses.put(player.getUniqueId(), used + 1);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!eventRunning) return;
        Player dead = event.getEntity();
        if (dead.getUniqueId().equals(runnerId)) {
            Bukkit.broadcast(Component.text("THE RUNNER HAS BEEN ELIMINATED! HUNTERS WIN!", NamedTextColor.RED));
            stopEvent();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!eventRunning) return;
        Player player = event.getPlayer();
        if (hunters.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> giveHunterKit(player), 5L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!eventRunning) return;
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(runnerId) && !hunters.contains(player.getUniqueId())) {
            hunters.add(player.getUniqueId());
            giveHunterKit(player);
            player.sendMessage(Component.text("You joined mid-event and have been added as a Hunter!", NamedTextColor.GREEN));
        }
    }
}
