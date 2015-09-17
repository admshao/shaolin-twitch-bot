package shaolin.twitch.bot;

import java.io.IOException;
import java.net.URISyntaxException;

import shaolin.twitch.bot.util.Config;

public class ShaolinTwitchBot {
	public static void main(String[] args) throws IOException, URISyntaxException {
		Config.initConfig();
		new IRC();
		System.out.println("To Vivo");
	}
}
