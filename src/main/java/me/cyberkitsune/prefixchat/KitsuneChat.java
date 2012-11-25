package me.cyberkitsune.prefixchat;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import net.milkbowl.vault.chat.Chat;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.onarandombox.MultiverseCore.MultiverseCore;

public class KitsuneChat extends JavaPlugin{
	
	public Logger mcLog = Logger.getLogger("Minecraft");
	public ChatParties party = new ChatParties(this);
	public KitsuneChatCommand exec = new KitsuneChatCommand(this);
	public UserMessaging msgExec = new UserMessaging(this);
	public KitsuneChatUserData dataFile;
	public KitsuneChatUtils util = new KitsuneChatUtils(this);
	public List<String> prefixes;
	public Chat vaultChat = null;
	public boolean vaultEnabled = false;
	public boolean multiVerse = false;
	public MultiverseCore multiversePlugin = null;
	
	@Override
	public void onEnable() {
		mcLog.info("[KitsuneChat] Enabling KitsuneChat version "+this.getDescription().getVersion()+"a");
		if(!(new File(getDataFolder()+"/config.yml").exists())) {
			mcLog.info("[KitsuneChat] KitsuneChat config does not exist! Creating default config...");
			if(!(new File(getDataFolder().toString()).isDirectory())) {
				new File(getDataFolder().toString()).mkdir();
			}
			try {
				new File(getDataFolder()+"/config.yml").createNewFile();
			} catch (Exception e) {
				mcLog.severe("[KitsuneChat] KitsuneChat could NOT create config file!!");
				e.printStackTrace();
			}
			loadConfig();
			setDefaults();
		}
		loadConfig();
		setDefaults();
		mcLog.info("[KitsuneChat] KitsuneChat config loaded!");
		if(setupVaultChat()) {
			vaultEnabled = true;
			mcLog.info("[KitsuneChat] Successfully linked to Vault for chat! Prefix / Suffix support enabled!");
		} else {
			vaultEnabled = false;
			mcLog.info("[KitsuneChat] Unable to link to Vault for chat! Is it installed? Prefix / Suffix support disabled!");
		}
		if(checkMultiverse()) {
			multiVerse = true;
			mcLog.info("[KitsuneChat] Multiverse found! Enabling world aliases!");
		} else {
			multiVerse = false;
			mcLog.info("[KitsuneChat] Did not find Multiverse. Not enabling world aliases.");
		}
		this.getServer().getPluginManager().registerEvents(new ChatListener(this), this);
		this.getServer().getPluginManager().registerEvents(new ConnectHandler(this), this);
		this.getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
		getCommand("kc").setExecutor(exec);
		PluginCommand meCmd = getCommand("me");
		PluginCommand msgCmd = getCommand("msg");
		PluginCommand repCmd = getCommand("r");
		PluginCommand memsgCmd = getCommand("memsg");
		PluginCommand merepCmd = getCommand("mer");
		
		if(meCmd == null)
		{
			mcLog.info("[KitsuneChat] Unable to hook /me ! Contact whoever broke your shit.");
		} else {
			meCmd.setExecutor(exec);
		}
		
		if(msgCmd == null || repCmd == null) {
			mcLog.info("[KitsuneChat] Unable to allocate /msg or /r, disabling PM support.");
		} else {
			msgCmd.setExecutor(msgExec);
			repCmd.setExecutor(msgExec);
		}
		
		if(memsgCmd == null || merepCmd == null) {
			mcLog.info("[KitsuneChat] Unable to allocate /memsg or /mer, disabling PM action support.");
		} else {
			memsgCmd.setExecutor(msgExec);
			merepCmd.setExecutor(msgExec);
		}
		
		dataFile = new KitsuneChatUserData(this);
		dataFile.loadUserData();
		
		Player[] online = getServer().getOnlinePlayers();
		//Put any online users back into their parties (in the event of a reload)
		for(Player plr : online) {
			party.changeParty(plr, dataFile.getPartyDataForUser(plr));
		}
		prefixes = Arrays.asList(this.getConfig().getString("global.prefix"), this.getConfig().getString("local.prefix"), this.getConfig().getString("staff.prefix"), this.getConfig().getString("admin.prefix"), this.getConfig().getString("party.prefix"), this.getConfig().getString("world.prefix"));
	}
	
	@Override
	public void onDisable() {
		// TODO Disable stuff?
		mcLog.info("[KitsuneChat] KitsuneChat disabled! You made CK cry ;~;");
	}
	
	private void loadConfig() {
		try {
			this.getConfig().load(getDataFolder()+"/config.yml");
		} catch (Exception e) {
			mcLog.severe("KitsuneChat could not load the config file!!");
			e.printStackTrace();
		} 
	}
	
	
	//TODO Cache config!
	private void setDefaults() {
		Configuration config = this.getConfig();
		if(!config.isSet("global.prefix")) {
			config.set("global.prefix", "!");
			config.set("world.prefix", "#");
			config.set("admin.prefix", "@");
			config.set("staff.prefix", "^");
			config.set("party.prefix", "$");
			config.set("local.prefix", "%");
			config.set("party.cost", 0);
			config.set("local.radius", 128);
		}
		if(!config.isSet("global.sayformat")) {
			config.set("global.sayformat", "[{world}] {sender}: {message}");
			config.set("admin.sayformat", "[{prefix}] {sender}: {message}");
			config.set("staff.sayformat", "[{prefix}] {sender}: {message}");
			config.set("world.sayformat", "[{prefix}] {sender}: {message}");
			config.set("party.sayformat", "[{party}] {sender}: {message}");
			config.set("local.sayformat", "{sender}: {message}");
		}
		
		if(!config.isSet("global.meformat")) {
			config.set("global.meformat", "[{world}] * {sender} {message}");
			config.set("admin.meformat", "[{prefix}] * {sender} {message}");
			config.set("staff.meformat", "[{prefix}] * {sender} {message}");
			config.set("world.meformat", "[{prefix}] * {sender} {message}");
			config.set("party.meformat", "[{party}] * {sender} {message}");
			config.set("local.meformat", "* {sender} {message}");
		}
		
		if(!config.isSet("emote.prefix")) {
			config.set("emote.prefix", "|");
		}
		
		if(!config.isSet("default")) {
			config.set("default", "%");
		}
		
		if(!config.isSet("local.warnifalone")) {
			config.set("local.warnifalone", true);
		}
		
		config.set("version", this.getDescription().getVersion());
		this.saveConfig();	
	}
	
	private boolean setupVaultChat() {
		if(getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Chat> chatProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
		if(chatProvider != null) {
			vaultChat = chatProvider.getProvider();
		}
		return (vaultChat != null);
	}
	
	private boolean checkMultiverse() {
		if(getServer().getPluginManager().getPlugin("Multiverse-Core") == null) {
			return false;
		} else {
			multiversePlugin = (MultiverseCore) getServer().getPluginManager().getPlugin("Multiverse-Core");
			return true;
		}
	}

}
