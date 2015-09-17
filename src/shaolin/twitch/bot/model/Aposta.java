package shaolin.twitch.bot.model;

public class Aposta {
	public long valor;
    public String username;
    public boolean vitoria;

    public Aposta(String username, long valor, boolean vitoria) {
        this.username = username;
        this.valor = valor;
        this.vitoria = vitoria;
    }
}
