package shaolin.twitch.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import shaolin.twitch.bot.model.Aposta;
import shaolin.twitch.bot.util.Config;

public class JogoDeApostas {
	private boolean running;
    private long vitoria;
    private long derrota;
    HashMap<String, Aposta> apostas;

    public JogoDeApostas() {
        this.derrota = this.vitoria = 0;
        this.running = true;
        this.apostas = new HashMap<String, Aposta>();
    }

    public boolean isRunning() {
        return this.running;
    }

    public void closeBets() {
        this.running = false;
    }

    public boolean addVitoria(String username, long pts) {
        if (Config.usuarios.containsKey(username) && Config.usuarios.get(username) >= pts) {
            addAposta(username, pts, true);
            return true;
        }
        return false;
    }

    public Aposta getAposta(String username) {
        return jaApostou(username) ? apostas.get(username) : null;
    }

    public boolean jaApostou(String username) {
        return apostas.containsKey(username);
    }

    public boolean addDerrota(String username, long pts) {
        if (Config.usuarios.containsKey(username) && Config.usuarios.get(username) >= pts) {
            addAposta(username, pts, false);
            return true;
        }
        return false;
    }

    private void addAposta(String username, long pts, boolean resultado) {
        apostas.put(username, new Aposta(username, pts, resultado));
        if (resultado) {
            vitoria += pts;
        } else {
            derrota += pts;
        }
    }

    public void saveBackup() {
        try {
			Files.write(Paths.get(Config.URI_APOSTAS), Config.GSON.toJson(this).getBytes(Config.CHARSET));
		} catch (IOException e) {
			System.err.println("Failed to store Jogo de Apostas data");
			e.printStackTrace();
			System.exit(-1);
		}
    }

    public static JogoDeApostas loadBackup() {
    	String apostasString;
		try {
			apostasString = new String(Files.readAllBytes(Paths.get(Config.URI_APOSTAS)), Config.CHARSET);
		} catch (IOException e) {
			System.err.println("Error parsing apostas.json");
			e.printStackTrace();
			return null;
		}
    	return Config.GSON.fromJson(apostasString, JogoDeApostas.class);
	}

    public List<Aposta> getApostas() {
        return new ArrayList<Aposta>(apostas.values());
    }

    public long totalEmVitoria() {
        return vitoria;
    }

    public long totalEmDerrota() {
        return derrota;
    }

    public long total() {
        return vitoria + derrota;
    }

    public void finalizarJogo() {
        this.derrota = this.vitoria = 0;
        this.apostas.clear();
        if (Files.exists(Paths.get(Config.URI_APOSTAS))) {
            try {
				Files.delete(Paths.get(Config.URI_APOSTAS));
			} catch (IOException e) {
				System.err.println("Error deleting apostas.json");
				e.printStackTrace();
			}
        }
    }
}
