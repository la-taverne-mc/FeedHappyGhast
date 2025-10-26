package fr.Like.feedHappyGhast;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FeedHappyGhast extends JavaPlugin implements Listener {

    private int effectDuration;
    private double speedBoost;

    private NamespacedKey gemKey;
    private NamespacedKey fragmentKey;
    private NamespacedKey recipeKey;

    private String gemName, fragmentName;
    private Material gemMaterial, fragmentMaterial;
    private List<String> gemLore, fragmentLore;
    private int gemModelData, fragmentModelData;
    private List<String> gemFlags, fragmentFlags;


    private final Map<UUID, Long> ghastEndTimes = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> playerTimerTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeEffects = new ConcurrentHashMap<>();


    @Override
    public void onEnable() {
        this.gemKey = new NamespacedKey(this, "feedhappyghast_gem");
        this.fragmentKey = new NamespacedKey(this, "feedhappyghast_fragment");
        // --- MODIFIÉ ---
        this.recipeKey = new NamespacedKey(this, "amazonite_recipe");

        this.saveDefaultConfig();
        this.loadConfig();
        getServer().getPluginManager().registerEvents(this, this);

        this.registerCraftingRecipe();
        getLogger().info("FeedHappyGhast activé avec objets personnalisés !");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        final List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("fhg")) {

            if (args.length == 1) {
                List<String> subCommands = new ArrayList<>();
                if (sender.hasPermission("feedhappyghast.give")) {
                    subCommands.add("give");
                }
                if (sender.hasPermission("feedhappyghast.reload")) {
                    subCommands.add("reload");
                }
                if (sender.hasPermission("feedhappyghast.disable")) {
                    subCommands.add("disable");
                }
                StringUtil.copyPartialMatches(args[0], subCommands, completions);
                return completions;
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("give") && sender.hasPermission("feedhappyghast.give")) {
                    // --- MODIFIÉ ---
                    List<String> itemTypes = Arrays.asList("amazonite", "fragment_amazonite");
                    StringUtil.copyPartialMatches(args[1], itemTypes, completions);
                    return completions;
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("give") && sender.hasPermission("feedhappyghast.give")) {
                    List<String> playerNames = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playerNames.add(player.getName());
                    }
                    StringUtil.copyPartialMatches(args[2], playerNames, completions);
                    return completions;
                }
            }
        }
        return completions;
    }

    @Override
    public void onDisable() {
        getLogger().info("Désactivation de FeedHappyGhast...");

        for (BukkitTask task : playerTimerTasks.values()) {
            task.cancel();
        }
        for (UUID playerId : playerTimerTasks.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(""));
            }
        }
        playerTimerTasks.clear();

        for (UUID ghastUUID : activeEffects.keySet()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier remove 1-1-1-1-1");
        }
        getLogger().info("Effets de vitesse retirés de " + activeEffects.size() + " Ghast(s).");

        activeEffects.clear();
        ghastEndTimes.clear();

        if (this.recipeKey != null) {
            Bukkit.removeRecipe(this.recipeKey);
            getLogger().info("Recette de craft retirée.");
        }

        getLogger().info("FeedHappyGhast a été désactivé avec succès.");
    }


    // --- MÉTHODE MISE À JOUR ---
    private void loadConfig() {
        this.reloadConfig();
        this.effectDuration = this.getConfig().getInt("duration_in_seconds");
        this.speedBoost = this.getConfig().getDouble("speed_boost");

        // --- MODIFIÉ: Lit "amazonite" ---
        this.gemMaterial = Material.valueOf(this.getConfig().getString("amazonite.base_material", "EMERALD").toUpperCase());
        this.gemName = translateColors(this.getConfig().getString("amazonite.name", "§bAmazonite"));
        this.gemLore = translateColors(this.getConfig().getStringList("amazonite.lore"));
        this.gemModelData = this.getConfig().getInt("amazonite.CustomModelData", 0);
        this.gemFlags = this.getConfig().getStringList("amazonite.flags");

        // --- MODIFIÉ: Lit "fragment_amazonite" ---
        this.fragmentMaterial = Material.valueOf(this.getConfig().getString("fragment_amazonite.base_material", "EMERALD").toUpperCase());
        this.fragmentName = translateColors(this.getConfig().getString("fragment_amazonite.name", "§fFragment d'Amazonite"));
        this.fragmentLore = translateColors(this.getConfig().getStringList("fragment_amazonite.lore"));
        this.fragmentModelData = this.getConfig().getInt("fragment_amazonite.CustomModelData", 0);
        this.fragmentFlags = this.getConfig().getStringList("fragment_amazonite.flags");
    }
    // --- FIN DE LA MISE À JOUR ---


    private void registerCraftingRecipe() {
        ItemStack gemResult = createCustomItem(gemMaterial, gemName, gemLore, gemKey, gemModelData, gemFlags);
        ItemStack fragmentIngredient = createCustomItem(fragmentMaterial, fragmentName, fragmentLore, fragmentKey, fragmentModelData, fragmentFlags);

        if (Bukkit.getRecipe(this.recipeKey) != null) {
            Bukkit.removeRecipe(this.recipeKey);
        }

        ShapedRecipe recipe = new ShapedRecipe(this.recipeKey, gemResult);
        recipe.shape(".F.",
                "FGF",
                ".F.");
        recipe.setIngredient('F', new RecipeChoice.ExactChoice(fragmentIngredient));
        recipe.setIngredient('G', Material.GHAST_TEAR);
        Bukkit.addRecipe(recipe);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("fhg")) {
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /fhg <give|reload|disable>");
                return false;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("feedhappyghast.reload")) {
                    this.loadConfig();
                    this.registerCraftingRecipe();
                    sender.sendMessage("§aConfiguration et recette de FeedHappyGhast rechargées !");
                    return true;
                } else {
                    sender.sendMessage("§cVous n'avez pas la permission (feedhappyghast.reload).");
                    return false;
                }
            }

            if (args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("feedhappyghast.give")) {
                    sender.sendMessage("§cVous n'avez pas la permission (feedhappyghast.give).");
                    return false;
                }

                if (args.length < 2) {
                    // --- MODIFIÉ ---
                    sender.sendMessage("§cUsage: /fhg give <amazonite|fragment_amazonite> [joueur] [quantité]");
                    return false;
                }

                Player target = (sender instanceof Player) ? (Player) sender : null;
                if (args.length >= 3) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage("§cLe joueur '" + args[2] + "' n'est pas en ligne.");
                        return false;
                    }
                }

                if (target == null) {
                    sender.sendMessage("§cVous devez spécifier un joueur (la console ne peut pas se give).");
                    return false;
                }

                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c'" + args[3] + "' n'est pas un nombre valide.");
                        return false;
                    }
                }

                ItemStack itemToGive;
                // --- MODIFIÉ ---
                if (args[1].equalsIgnoreCase("amazonite")) {
                    itemToGive = createCustomItem(gemMaterial, gemName, gemLore, gemKey, gemModelData, gemFlags);
                    itemToGive.setAmount(amount);
                    target.getInventory().addItem(itemToGive);
                    sender.sendMessage("§aDonné " + amount + " Amazonite à " + target.getName());

                    // --- MODIFIÉ ---
                } else if (args[1].equalsIgnoreCase("fragment_amazonite")) {
                    itemToGive = createCustomItem(fragmentMaterial, fragmentName, fragmentLore, fragmentKey, fragmentModelData, fragmentFlags);
                    itemToGive.setAmount(amount);
                    target.getInventory().addItem(itemToGive);
                    sender.sendMessage("§aDonné " + amount + " Fragment(s) d'Amazonite à " + target.getName());

                } else {
                    // --- MODIFIÉ ---
                    sender.sendMessage("§cObjet inconnu. Utilisez 'amazonite' ou 'fragment_amazonite'.");
                    return false;
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("disable")) {
                if (sender.hasPermission("feedhappyghast.disable")) {
                    sender.sendMessage("§cDésactivation de FeedHappyGhast en cours...");
                    Bukkit.getPluginManager().disablePlugin(this);
                    sender.sendMessage("§aPlugin désactivé.");
                    return true;
                } else {
                    sender.sendMessage("§cVous n'avez pas la permission (feedhappyghast.disable).");
                    return false;
                }
            }
        }

        if (command.getName().equalsIgnoreCase("fhgreload")) {
            if (sender.hasPermission("feedhappyghast.reload")) {
                this.loadConfig();
                this.registerCraftingRecipe();
                sender.sendMessage("§aConfiguration et recette de FeedHappyGhast rechargées !");
                return true;
            } else {
                sender.sendMessage("§cVous n'avez pas la permission (feedhappyghast.reload).");
                return false;
            }
        }

        return false;
    }


    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.valueOf("HAPPY_GHAST")) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();
            LivingEntity happyGhast = (LivingEntity) event.getRightClicked();
            UUID ghastUUID = happyGhast.getUniqueId();

            if (!isCustomItem(item, gemKey)) {
                return;
            }

            if (activeEffects.containsKey(ghastUUID)) {
                player.sendMessage("§cCe Happy Ghast est déjà sous l'effet de vitesse et ne peut pas être nourri de nouveau pour le moment.");
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            activeEffects.put(ghastUUID, player.getUniqueId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier add 1-1-1-1-1 " + this.speedBoost + " add_value");
            player.sendMessage("§aVous avez nourri le Happy Ghast ! Sa vitesse a augmenté.");
            long endTime = System.currentTimeMillis() + (this.effectDuration * 1000L);
            ghastEndTimes.put(ghastUUID, endTime);
            startEffectTimer(player);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (happyGhast.isValid()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier remove 1-1-1-1-1");
                        Player feeder = Bukkit.getPlayer(player.getUniqueId());
                        if(feeder != null && feeder.isOnline()) {
                            feeder.sendMessage("§cL'effet de vitesse du Happy Ghast est terminé.");
                        }
                    }
                    activeEffects.remove(ghastUUID);
                    ghastEndTimes.remove(ghastUUID);
                }
            }.runTaskLater(this, this.effectDuration * 20L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask task = playerTimerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        boolean isOwnerOfActiveGhast = activeEffects.containsValue(playerId);
        if (isOwnerOfActiveGhast) {
            startEffectTimer(player);
        }
    }

    private void startEffectTimer(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask existingTask = playerTimerTasks.get(playerId);
        if (existingTask != null) existingTask.cancel();

        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !playerTimerTasks.containsKey(playerId)) {
                    this.cancel();
                    return;
                }
                Entity vehicle = player.getVehicle();
                if (vehicle != null && vehicle.getType() == EntityType.valueOf("HAPPY_GHAST")) {
                    UUID ghastUUID = vehicle.getUniqueId();
                    if (ghastEndTimes.containsKey(ghastUUID)) {
                        long currentTime = System.currentTimeMillis();
                        long endTime = ghastEndTimes.get(ghastUUID);

                        if (currentTime >= endTime) {
                            player.sendActionBar(Component.text(""));
                            return;
                        }

                        long timeLeftSeconds = (endTime - currentTime) / 1000;
                        long minutes = timeLeftSeconds / 60;
                        long seconds = timeLeftSeconds % 60;
                        String timeFormatted = String.format("%01d:%02d", minutes, seconds);

                        String message = String.format("§6⚡ §fVitesse Ghast: §e%s", timeFormatted);
                        player.sendActionBar(Component.text(message));
                        return;
                    }
                }
                player.sendActionBar(Component.text(""));
            }
        }.runTaskTimer(this, 0L, 5L);

        playerTimerTasks.put(playerId, timerTask);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }

        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;

            // 1. SI C'EST NOTRE RECETTE :
            if (shapedRecipe.getKey().equals(this.recipeKey)) {

                // Force la création de l'item complet (avec nom, lore, etc.)
                ItemStack result = createCustomItem(gemMaterial, gemName, gemLore, gemKey, gemModelData, gemFlags);
                event.getInventory().setResult(result);
                return;
            }
        }

        // 2. SI CE N'EST PAS NOTRE RECETTE (Bloque les crafts illégaux)
        ItemStack[] matrix = event.getInventory().getMatrix();
        for (ItemStack item : matrix) {
            if (item == null) {
                continue;
            }
            if (isCustomItem(item, gemKey) || isCustomItem(item, fragmentKey)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }


    private ItemStack createCustomItem(Material material, String name, List<String> lore, NamespacedKey key, int modelData, List<String> flags) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);
        meta.setLore(lore);

        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }

        if (flags != null && !flags.isEmpty()) {
            for (String flagName : flags) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Flag invalide dans config.yml: " + flagName);
                }
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    private boolean isCustomItem(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(key, PersistentDataType.BYTE) && pdc.get(key, PersistentDataType.BYTE) == (byte) 1;
    }

    private String translateColors(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }


    private List<String> translateColors(List<String> texts) {
        List<String> translated = new ArrayList<>();
        if (texts == null) return translated;
        for (String line : texts) {
            translated.add(translateColors(line));
        }
        return translated;
    }

    private void cleanUpTask(UUID playerId) {
        BukkitTask task = playerTimerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(""));
            }
        }
    }
}