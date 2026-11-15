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

import java.time.Duration;
import java.util.*;

public class HuntersEvent extends JavaPlugin implements Listener, CommandExecutor {

    private UUID runnerUUID = null;
    private boolean eventRunning = false;
    private int currentStage = 1;
    private boolean bloodMoonActive = false;
    private int totemLimit = 3;
    private int runnerKills = 0;

    private final Map<UUID, Integer> totemsUsed = new HashMap<>();
    private final Set<UUID> aliveHunters = new HashSet<>();
    private final Map<UUID, Long> trackerCooldowns = new HashMap<>();

    private BukkitTask compassTask;
    private BukkitTask scoreboardTask;
    private BukkitTask bloodMoonTask;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(this);
        }
        getLogger().info("HuntersEvent 100 Hunters vs 1 PvPer enabled!");
    }

    @Override
    public void onDisable() {
        stopEventTasks();
    }

    private void stopEventTasks() {
        if (compassTask != null) compassTask.cancel();
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (bloodMoonTask != null) bloodMoonTask.cancel();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("huntersevent.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /event <runner|start|stage|bloodmoon|totemlimit|supplydrop|stop>", NamedTextColor.YELLOW));
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
                runnerUUID = target.getUniqueId();
                Bukkit.broadcast(Component.text("Runner set to: " + target.getName(), NamedTextColor.GOLD));
            }
            case "start" -> {
                if (runnerUUID == null || Bukkit.getPlayer(runnerUUID) == null) {
                    sender.sendMessage(Component.text("Set an online runner first with /event runner <player>!", NamedTextColor.RED));
                    return true;
                }
                startEvent();
            }
            case "stage" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /event stage <1|2|3|4>", NamedTextColor.RED));
                    return true;
                }
                try {
                    int stage = Integer.parseInt(args[1]);
                    if (stage < 1 || stage > 4) throw new NumberFormatException();
                    setStage(stage);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Stage must be 1, 2, 3, or 4.", NamedTextColor.RED));
                }
            }
            case "bloodmoon" -> toggleBloodMoon();
            case "totemlimit" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /event totemlimit <number>", NamedTextColor.RED));
                    return true;
                }
                try {
                    totemLimit = Integer.parseInt(args[1]);
                    Bukkit.broadcast(Component.text("Runner Totem Limit set to: " + totemLimit, NamedTextColor.LIGHT_PURPLE));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                }
            }
            case "supplydrop" -> spawnSupplyDrop();
            case "stop" -> {
                eventRunning = false;
                bloodMoonActive = false;
                stopEventTasks();
                Bukkit.broadcast(Component.text("The 100 Hunters Event has been stopped!", NamedTextColor.RED));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private void startEvent() {
        eventRunning = true;
        currentStage = 1;
        runnerKills = 0;
        totemsUsed.clear();
        aliveHunters.clear();

        Player runner = Bukkit.getPlayer(runnerUUID);
        if (runner == null) return;

        // Setup Runner
        setMaxHealth(runner, 40.0); // 20 hearts
        runner.setHealth(40.0);
        runner.setFoodLevel(20);
        runner.getInventory().clear();
        equipRunnerKit(runner);

        // Setup Hunters
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(runnerUUID)) {
                aliveHunters.add(p.getUniqueId());
                setMaxHealth(p, 20.0);
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                equipHunterStageKit(p, 1);
            }
        }

        Bukkit.broadcast(Component.text("========================================", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text("  100 HUNTERS VS 1 PVPER HAS STARTED!  ", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text("  Runner: " + runner.getName() + " | Stage 1 Active", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text("========================================", NamedTextColor.DARK_RED));

        startTasks();
    }

    private void setStage(int stage) {
        currentStage = stage;
        Bukkit.broadcast(Component.text("HUNTERS HAVE REACHED STAGE " + stage + "!", NamedTextColor.RED));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            if (!p.getUniqueId().equals(runnerUUID) && aliveHunters.contains(p.getUniqueId())) {
                equipHunterStageKit(p, stage);
            }
        }
    }

    private void toggleBloodMoon() {
        bloodMoonActive = !bloodMoonActive;
        Player runner = runnerUUID != null ? Bukkit.getPlayer(runnerUUID) : null;

        if (bloodMoonActive) {
            if (runner != null) {
                runner.getWorld().setTime(18000);
                runner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            }
            Bukkit.broadcast(Component.text("BLOOD MOON RISES! The Runner is Glowing & Mobs Swarm!", NamedTextColor.DARK_RED));

            bloodMoonTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!bloodMoonActive || !eventRunning) {
                        cancel();
                        return;
                    }
                    Player r = runnerUUID != null ? Bukkit.getPlayer(runnerUUID) : null;
                    if (r != null && r.isOnline()) {
                        Location loc = r.getLocation().add((Math.random() - 0.5) * 20, 1, (Math.random() - 0.5) * 20);
                        Zombie z = (Zombie) r.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                        z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 1));
                    }
                }
            }.runTaskTimer(this, 100L, 200L);
        } else {
            if (runner != null) {
                runner.removePotionEffect(PotionEffectType.GLOWING);
            }
            if (bloodMoonTask != null) bloodMoonTask.cancel();
            Bukkit.broadcast(Component.text("The Blood Moon has faded.", NamedTextColor.GREEN));
        }
    }

    private void spawnSupplyDrop() {
        Player runner = runnerUUID != null ? Bukkit.getPlayer(runnerUUID) : null;
        if (runner == null) return;

        Location dropLoc = runner.getLocation().add((Math.random() - 0.5) * 80, 0, (Math.random() - 0.5) * 80);
        dropLoc.setY(runner.getWorld().getHighestBlockYAt(dropLoc) + 1);

        Block block = dropLoc.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
            inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            inv.addItem(new ItemStack(Material.ENDER_PEARL, 8));
            inv.addItem(new ItemStack(Material.END_CRYSTAL, 16));
            inv.addItem(new ItemStack(Material.OBSIDIAN, 32));
        }

        runner.getWorld().strikeLightningEffect(dropLoc);
        Bukkit.broadcast(Component.text("SUPPLY DROP SPAWNED AT: X=" + dropLoc.getBlockX() + " Y=" + dropLoc.getBlockY() + " Z=" + dropLoc.getBlockZ(), NamedTextColor.AQUA));
    }

    private void startTasks() {
        stopEventTasks();

        // Compass tracking every 2 seconds
        compassTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventRunning || runnerUUID == null) return;
                Player runner = Bukkit.getPlayer(runnerUUID);
                if (runner == null || !runner.isOnline()) return;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getUniqueId().equals(runnerUUID)) continue;
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.getType() == Material.COMPASS) {
                            if (item.getItemMeta() instanceof CompassMeta meta) {
                                meta.setLodestone(runner.getLocation());
                                meta.setLodestoneTracked(false);
                                item.setItemMeta(meta);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);

        // Scoreboard update every 1 second
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventRunning) return;
                updateScoreboards();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void updateScoreboards() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("huntersevent", Criteria.DUMMY, Component.text("100 HUNTERS EVENT", NamedTextColor.RED));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Player runner = runnerUUID != null ? Bukkit.getPlayer(runnerUUID) : null;
        String runnerName = runner != null ? runner.getName() : "None";
        int used = runnerUUID != null ? totemsUsed.getOrDefault(runnerUUID, 0) : 0;

        obj.getScore("§7----------------").setScore(7);
        obj.getScore("§6Runner: §f" + runnerName).setScore(6);
        obj.getScore("§cHunters Alive: §f" + aliveHunters.size()).setScore(5);
        obj.getScore("§eRunner Kills: §f" + runnerKills).setScore(4);
        obj.getScore("§bStage: §f" + currentStage + "/4").setScore(3);
        obj.getScore("§dTotems Used: §f" + used + "/" + totemLimit).setScore(2);
        obj.getScore("§4Blood Moon: §f" + (bloodMoonActive ? "ACTIVE" : "OFF")).setScore(1);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent
