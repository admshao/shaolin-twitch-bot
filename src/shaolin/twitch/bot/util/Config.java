package shaolin.twitch.bot.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import shaolin.twitch.bot.ShaolinTwitchBot;

public class Config {
	public static URI URI_CONFIG = null;
	public static URI URI_USUARIOS = null;
	public static URI URI_APOSTAS = null;
	
	public static final Gson GSON = new Gson();
	public static final Charset CHARSET = Charset.forName("UTF-8");
	
	public static final Random RANDOM = new Random(System.nanoTime());
	
	public static HashMap<String, Long> usuarios = new HashMap<String, Long>();
    public static HashMap<String, String> comandos = new HashMap<String, String>();
    public static HashMap<String, String> saudacoes = new HashMap<String, String>();
	
	public static HashSet<String> mods = new HashSet<>();
    public static HashSet<String> blacklist = new HashSet<>();

    public static String HOST;
    public static int PORT;
    public static String CHANNEL;
    public static String PASS;
    public static String NICK;
    public static String VIEWERS_URL;
    public static String CAP;
    
    public static final String END_LINE = "\r\n";
    public static String WHITE_SPACE = " ";
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.00");

    public static String REWARD_INITIAL_POINTS;
    public static int REWARD_INTERVAL_BETS;
    public static int REWARD_INTERVAL_BETWEEN_BETS;
    public static int REWARD_INTERVAL_MMR;
    public static int REWARD_INTERVAL_BONUS;
    public static int REWARD_INTERVAL_BONUS_MMR;
    public static int REWARD_POINTS;

    public static void givePoints(String user) {
        if (blacklist.contains(user)) return;
        if (!Config.usuarios.containsKey(user)) {
            String[] split = REWARD_INITIAL_POINTS.split(";");
            Config.usuarios.put(user, (long) RANDOM.nextInt(Integer.valueOf(split[1]) - Integer.valueOf(split[0])) + Integer.valueOf(split[0]));
        } else {
        	Config.usuarios.put(user, Config.usuarios.get(user) + Config.REWARD_POINTS);
        }
    }

    public static String getCommands() throws IOException, URISyntaxException {
        StringBuilder sb = new StringBuilder();
        String configString = new String(Files.readAllBytes(Paths.get(URI_CONFIG)), CHARSET);
    	JsonElement cfgFileElement = GSON.fromJson(configString, JsonElement.class);
    	JsonObject cfgFileObj = cfgFileElement.getAsJsonObject();
    	JsonArray commandsObj = cfgFileObj.get("shaolinbot").getAsJsonObject().get("commands").getAsJsonArray();
    	
    	Iterator<JsonElement> iterator = commandsObj.iterator();
        while(iterator.hasNext()) {
        	JsonObject obj = iterator.next().getAsJsonObject();
        	if (obj.get("visible").getAsBoolean()) {
                sb.append(obj.get("trigger").getAsString() + Config.WHITE_SPACE);
            }
        }

        return sb.toString();
    }
    
    private static void prepareURIs() {
    	try {
			URI_CONFIG = new URI("file://" +ShaolinTwitchBot.class.getProtectionDomain().getCodeSource().getLocation().toURI().getRawPath()+"config.json");
			URI_USUARIOS = new URI("file://" +ShaolinTwitchBot.class.getProtectionDomain().getCodeSource().getLocation().toURI().getRawPath()+"usuarios.json");
			URI_APOSTAS = new URI("file://" +ShaolinTwitchBot.class.getProtectionDomain().getCodeSource().getLocation().toURI().getRawPath()+"apostas.json");
		} catch (URISyntaxException e) {
			System.err.println("Failed to parse URIs for config files");
			e.printStackTrace();
			System.exit(-1);
		}
    }
    
    public static void initConfig() throws IOException, URISyntaxException {
    	prepareURIs();
    	
    	String configString = new String(Files.readAllBytes(Paths.get(URI_CONFIG)), CHARSET);
    	JsonElement cfgFileElement = GSON.fromJson(configString, JsonElement.class);
    	JsonObject cfgFileObj = cfgFileElement.getAsJsonObject();
    	
    	JsonObject cfgObj = cfgFileObj.get("shaolinbot").getAsJsonObject().get("config").getAsJsonObject();

        HOST = cfgObj.get("host").getAsString();
        PORT = cfgObj.get("port").getAsInt();
        CHANNEL = cfgObj.get("channel").getAsString();
        PASS = cfgObj.get("pass").getAsString();
        NICK = cfgObj.get("account").getAsString();
        VIEWERS_URL = cfgObj.get("viewers").getAsString();
        VIEWERS_URL = VIEWERS_URL.replace("{channel}", CHANNEL);
        CAP = cfgObj.get("cap").getAsString();
        
        JsonArray saudacoesObj = cfgFileObj.get("shaolinbot").getAsJsonObject().get("greetings").getAsJsonArray();
        Iterator<JsonElement> iterator = saudacoesObj.iterator();
        while(iterator.hasNext()) {
        	JsonObject obj = iterator.next().getAsJsonObject();
        	String[] split = obj.get("trigger").getAsString().split(";");
        	
        	for (String s : split) {
        		if (saudacoes.containsKey(s)) {
                    saudacoes.remove(s);
                }
                saudacoes.put(s, obj.get("message").getAsString());
        	}
        }

        JsonObject rewardsObj = cfgFileObj.get("shaolinbot").getAsJsonObject().get("rewards").getAsJsonObject();

        REWARD_INITIAL_POINTS = rewardsObj.get("initial").getAsString();
        REWARD_INTERVAL_BETS = rewardsObj.get("interval_bets").getAsInt();
        REWARD_INTERVAL_BETWEEN_BETS = rewardsObj.get("interval_between_bets").getAsInt();
        REWARD_INTERVAL_MMR = rewardsObj.get("interval_mmr").getAsInt();
        REWARD_INTERVAL_BONUS = rewardsObj.get("interval_bonus").getAsInt();
        REWARD_INTERVAL_BONUS_MMR = rewardsObj.get("interval_bonus_mmr").getAsInt();
        REWARD_POINTS = rewardsObj.get("points").getAsInt();

        for (String s : cfgObj.get("mods").getAsString().split(";")) {
            mods.add(s);
        }

        for (String s : cfgObj.get("blacklist").getAsString().split(";")) {
            blacklist.add(s);
        }

        JsonArray commandsObj = cfgFileObj.get("shaolinbot").getAsJsonObject().get("commands").getAsJsonArray();
        iterator = commandsObj.iterator();
        while(iterator.hasNext()) {
        	JsonObject obj = iterator.next().getAsJsonObject();
        	String trigger = obj.get("trigger").getAsString();
        	if (comandos.containsKey(trigger)) {
                comandos.remove(trigger);
            }
            comandos.put(trigger, obj.get("message") == null ? "" : obj.get("message").getAsString());
        }

        if (Files.exists(Paths.get(URI_USUARIOS))) {
        	String usuariosString = new String(Files.readAllBytes(Paths.get(URI_USUARIOS)), Charset.forName("UTF-8"));
        	JsonElement usuariosFileElement = GSON.fromJson(usuariosString, JsonElement.class);
        	JsonObject usuariosObj = usuariosFileElement.getAsJsonObject();
        	
        	Iterator<Entry<String, JsonElement>> usuariosIterator = usuariosObj.entrySet().iterator();
            while(usuariosIterator.hasNext()) {
            	Entry<String, JsonElement> entry = usuariosIterator.next();
        		usuarios.put(entry.getKey(), entry.getValue().getAsLong());
            }
        }
    }

    public static void saveUsers() {
    	try {
			Files.write(Paths.get(URI_USUARIOS), GSON.toJson(usuarios).getBytes(CHARSET));
		} catch (IOException e) {
			System.err.println("Failed to store users data");
			e.printStackTrace();
			System.exit(-1);
		}
    }

    public static void loadCommands() throws IOException, URISyntaxException {
    	String configString = new String(Files.readAllBytes(Paths.get(URI_CONFIG)), CHARSET);
    	JsonElement cfgFileElement = GSON.fromJson(configString, JsonElement.class);
    	JsonObject cfgFileObj = cfgFileElement.getAsJsonObject();
    	JsonArray commandsObj = cfgFileObj.get("shaolinbot").getAsJsonObject().get("commands").getAsJsonArray();
    	
    	Iterator<JsonElement> iterator = commandsObj.iterator();
        while(iterator.hasNext()) {
        	JsonObject obj = iterator.next().getAsJsonObject();
        	String trigger = obj.get("trigger").getAsString();
        	if (comandos.containsKey(trigger)) {
                comandos.remove(trigger);
            }
            comandos.put(trigger, obj.get("message") == null ? "" : obj.get("message").getAsString());
        }
    }
}
