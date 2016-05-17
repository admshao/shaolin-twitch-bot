package shaolin.twitch.bot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import shaolin.twitch.bot.model.Aposta;
import shaolin.twitch.bot.util.Config;

public class IRC {

	private boolean running;

	private Socket socket;
	private BufferedReader in;
	private BufferedWriter out;
	
	private Queue<String[]> commands;
	
	private JogoDeApostas jogoDeApostas;
	private Thread listen, sender, viewer, anuncioAposta, recompensaAposta;
	
	private List<Entry<String, Long>> usuarios;
	private final Comparator<Entry<String, Long>> byValue = (entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue());

	public IRC() {
		commands = new LinkedList<>();

		try {
			socket = new Socket(Config.HOST, Config.PORT);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), Config.CHARSET));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Config.CHARSET));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		handleLogin();
		
		usuarios = Config.usuarios.entrySet().stream().sorted(byValue).collect(Collectors.toList());
		
		listen = new Thread(new Listen());
		listen.start();
		
		sender = new Thread(new Sender());
		sender.start();
		
		viewer = new Thread(new Viewer());
		viewer.start();
		
		if (Files.exists(Paths.get(Config.URI_APOSTAS))) {
            jogoDeApostas = JogoDeApostas.loadBackup();
        }
	}
	
	public class Listen implements Runnable {
		@Override
		public void run() {
			String[] ex;
			String cmd;
			String line;
			while (true) {
				try {
					while ((line = in.readLine()) != null) {
						System.out.println(line);
						
						if (running) {
	                        ex = line.split(Config.WHITE_SPACE, 4);
	                        if (ex[0].equals("PING")) {
	                            sendMessage("PONG " + ex[1]);
	                        } else if (ex[1].equals("PRIVMSG")) {
	                            cmd = ex[3].substring(1, ex[3].length()).toLowerCase();

	                            if (Config.saudacoes.containsKey(cmd) || Config.saudacoes.containsKey(cmd.split(Config.WHITE_SPACE)[0]) || Config.comandos.containsKey(cmd) || Config.comandos.containsKey(cmd.split(Config.WHITE_SPACE)[0])) {
	                                commands.add(ex);
	                            }
	                        }
	                    }
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public class Viewer implements Runnable {
		@Override
		public void run() {
			int i = 0;
            while (true) {
                try {
                	URLConnection connection = new URL(Config.VIEWERS_URL).openConnection();
                	BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                	
                	StringBuilder sb = new StringBuilder();
                	String line;
                	while ((line = br.readLine()) != null) {
                		sb.append(line);
					}

                	JsonObject chattersObj = Config.GSON.fromJson(sb.toString(), JsonObject.class);
                    if (chattersObj != null) {
                    	JsonObject chatters = chattersObj.get("chatters").getAsJsonObject();
                    	JsonArray moderators = chatters.get("moderators").getAsJsonArray();
                    	JsonArray viewers = chatters.get("viewers").getAsJsonArray();
                        for (JsonElement username : moderators) {
                            Config.givePoints(username.getAsString());
                        }
                        for (JsonElement username : viewers) {
                            Config.givePoints(username.getAsString());
                        }

                        if (++i == Config.REWARD_INTERVAL_BONUS) {
                            i = 0;
                            if (viewers.size() > 0) {
                            	String usr = viewers.get(Config.RANDOM.nextInt(viewers.size())).getAsString();
                            	Config.usuarios.put(usr, Config.usuarios.get(usr) + Config.REWARD_INTERVAL_BONUS_MMR);
                            	sendChatMessage(usr + " ganhou um bônus de " + Config.REWARD_INTERVAL_BONUS_MMR + " MMR Kreygasm");
                            }
                        }

                        Config.saveUsers();
                		usuarios = Config.usuarios.entrySet().stream().sorted(byValue).collect(Collectors.toList());
                    }

                    Thread.sleep(Config.REWARD_INTERVAL_MMR * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
		}
	}
	
	public class RecompensaAposta implements Runnable {
		String result;
		public RecompensaAposta(String res) {
			result = res;
		}

		@Override
		public void run() {
			try {
                String resp = null;
                if (result.equals("cancel")) {
                    jogoDeApostas.finalizarJogo();
                    jogoDeApostas = null;

                    Config.saveUsers();

                    resp = "Apostas canceladas. Os pontos já foram devolvidos.. ResidentSleeper";
                } else {
                    long total = jogoDeApostas.total();
                    long totalVencedores = result.equals("vitoria") ? jogoDeApostas.totalEmVitoria() : jogoDeApostas.totalEmDerrota();
                    HashSet<String> ganhadores = new HashSet<>();
                    long max = 0;
                    String user = "Ninguém";

                    for (Aposta aposta : jogoDeApostas.getApostas()) {
                        if (aposta.vitoria == result.equals("vitoria")) {
                            ganhadores.add(aposta.username);
                            double fator = 100;

                        	Entry<String, Long> usuario = usuarios.stream().filter(a -> a.getKey().equals(aposta.username)).findFirst().get();
                            int pos = usuarios.indexOf(usuario);
                            if (pos < 10) {
                                fator += 10 - pos;
                            }
                            long valorNoTotal = (long) (Math.round(total * (aposta.valor * 100.0 / totalVencedores)) / 100.0);
                            valorNoTotal = (long)(valorNoTotal * (100 / fator));

                        	Config.usuarios.put(aposta.username, Config.usuarios.get(aposta.username) + (valorNoTotal - aposta.valor));

                            if (valorNoTotal > max) {
                                max = valorNoTotal;
                                user = aposta.username;
                            }
                        } else {
                        	Config.usuarios.put(aposta.username, Config.usuarios.get(aposta.username) - aposta.valor);
                        }
                    }

                    jogoDeApostas.finalizarJogo();
                    jogoDeApostas = null;

                    Config.saveUsers();
                    usuarios = Config.usuarios.entrySet().stream().sorted(byValue).collect(Collectors.toList());

                    resp = Config.comandos.get("!finish");
                    resp = resp.replace("{resultado}", result);
                    resp = resp.replace("{numero}", String.valueOf(ganhadores.size()));
                    resp = resp.replace("{max}", String.valueOf(max));
                    resp = resp.replace("{maior}", user);
                }
                sendChatMessage(resp);
            } catch (Exception e) {
                e.printStackTrace();
            }
		}
	}
	
	public class AnuncioAposta implements Runnable {
		@Override
		public void run() {
			try {
                for (int i = 0; i < Config.REWARD_INTERVAL_BETS; i++) {
                    Thread.sleep(Config.REWARD_INTERVAL_BETWEEN_BETS * 1000);
                    printOdds(i);
                }
                jogoDeApostas.closeBets();
                if (!jogoDeApostas.getApostas().isEmpty()) {
                    sendChatMessage("Apostas encerradas SwiftRage");
                    jogoDeApostas.saveBackup();
                } else {
                    sendChatMessage("Nenhuma aposta feita. Jogo cancelado..");
                    jogoDeApostas.finalizarJogo();
                    jogoDeApostas = null;
                }
            } catch (Exception e) {
            	if (e.getMessage().equals("sleep interrupted")) {
            		System.err.println("Apostas canceladas");
            	} else {
            		e.printStackTrace();
            	}
            }
		}
	}
	
	public class Sender implements Runnable {
		@Override
		public void run() {
			String[] ex;
            String cmd;
            String resp;
            while (true) {
            	try {
            		Thread.sleep(340);
            		if (commands.size() > 0) {
                        ex = commands.poll();
                        cmd = ex[3].substring(1, ex[3].length()).toLowerCase();
                        final String nick = ex[0].substring(1, ex[0].length()).split("!")[0];

                        if (Config.saudacoes.containsKey(cmd) || Config.saudacoes.containsKey(cmd.split(Config.WHITE_SPACE)[0])) {
                            resp = Config.saudacoes.containsKey(cmd) ? Config.saudacoes.get(cmd) : Config.saudacoes.containsKey(cmd.split(Config.WHITE_SPACE)[0]) ? Config.saudacoes.get(cmd.split(Config.WHITE_SPACE)[0]) : null;
                            if (resp.contains(";")) {
                                ex = resp.split(";");
                                resp = ex[Config.RANDOM.nextInt(ex.length)];
                            }
                        } else {
                            resp = Config.comandos.containsKey(cmd) ? Config.comandos.get(cmd) : Config.comandos.containsKey(cmd.split(Config.WHITE_SPACE)[0]) ? Config.comandos.get(cmd.split(Config.WHITE_SPACE)[0]) : null;

                            if (cmd.equals("!start")) {
                                if (Config.mods.contains(nick)) {
                                    if (jogoDeApostas != null) {
                                        resp = "Já existe um jogo em andamento";
                                    } else {
                                        jogoDeApostas = new JogoDeApostas();

                                        anuncioAposta = new Thread(new AnuncioAposta());
                                        anuncioAposta.start();

                                        resp = resp.replace("{tempo}", String.valueOf(Config.REWARD_INTERVAL_BETS));
                                    }
                                } else {
                                    continue;
                                }
                            } else if (cmd.split(Config.WHITE_SPACE)[0].equals("!finish")) {
                                if (Config.mods.contains(nick)) {
                                    ex = cmd.split(Config.WHITE_SPACE);
                                    if (ex.length > 1 && (ex[1].equals("vitoria") || ex[1].equals("derrota") || ex[1].equals("cancel"))) {
                                        if (jogoDeApostas != null) {
                                            if (anuncioAposta != null && anuncioAposta.isAlive()) {
                                                anuncioAposta.interrupt();
                                                anuncioAposta = null;
                                            }

                                            recompensaAposta = new Thread(new RecompensaAposta(ex[1]));
                                            recompensaAposta.start();
                                            resp = null;
                                        } else {
                                            resp = "Nenhum jogo em andamento";
                                        }
                                    } else {
                                        resp = "\"" + cmd + "\" não é um comando válido.";
                                    }
                                } else {
                                    continue;
                                }
                            } else if (cmd.split(Config.WHITE_SPACE)[0].equals("!vitoria")) {
                            	if (jogoDeApostas != null && jogoDeApostas.isRunning()) {
                                    ex = cmd.split(Config.WHITE_SPACE);
                                    if (ex.length > 1) {
                                    	String value = cmd.split(Config.WHITE_SPACE)[1];
                                    	long pts;
                                    	try {
                                    		pts = Long.parseLong(value);
                                    		if (!jogoDeApostas.jaApostou(nick)) {
	                                            if (!jogoDeApostas.addVitoria(nick, pts)) {
	                                                resp = "{nick} - Saldo insuficiente.";
	                                            }
	                                        } else {
	                                            resp = "{nick} - Não é possível apostar mais de 1 vez.";
	                                        }
                                    	} catch (NumberFormatException e) {
                                    		if (cmd.split(Config.WHITE_SPACE)[1].equals("allin")) {
                                    			if (!jogoDeApostas.jaApostou(nick)) {
                                    				pts = Config.usuarios.containsKey(nick) ? Config.usuarios.get(nick) : 0;
                                                    if (!jogoDeApostas.addVitoria(nick, pts)) {
                                                        resp = "{nick} - Saldo insuficiente.";
                                                    } else {
                                                        resp = "{nick} Deu ALL IN (" + pts + ") Kreygasm";
                                                    }
                                                } else {
                                                    resp = "{nick} - Não é possível apostar mais de 1 vez.";
                                                }
                                    		} else {
                                    			resp = "{nick} - Aposte um valor válido";
                                    		}
                                    	}
                                    } else {
                                    	resp = "{nick} - Aposte um valor válido";
                                    }
                            	}
                            } else if (cmd.split(Config.WHITE_SPACE)[0].equals("!derrota")) {
                            	if (jogoDeApostas != null && jogoDeApostas.isRunning()) {
                                    ex = cmd.split(Config.WHITE_SPACE);
                                    if (ex.length > 1) {
                                    	String value = cmd.split(Config.WHITE_SPACE)[1];
                                    	long pts;
                                    	try {
                                    		pts = Long.parseLong(value);
                                    		if (!jogoDeApostas.jaApostou(nick)) {
	                                            if (!jogoDeApostas.addDerrota(nick, pts)) {
	                                                resp = "{nick} - Saldo insuficiente.";
	                                            }
	                                        } else {
	                                            resp = "{nick} - Não é possível apostar mais de 1 vez.";
	                                        }
                                    	} catch (NumberFormatException e) {
                                    		if (cmd.split(Config.WHITE_SPACE)[1].equals("allin")) {
                                    			if (!jogoDeApostas.jaApostou(nick)) {
                                    				pts = Config.usuarios.containsKey(nick) ? Config.usuarios.get(nick) : 0;
                                                    if (!jogoDeApostas.addDerrota(nick, pts)) {
                                                        resp = "{nick} - Saldo insuficiente.";
                                                    } else {
                                                        resp = "{nick} Deu ALL IN (" + pts + ") Kappa";
                                                    }
                                                } else {
                                                    resp = "{nick} - Não é possível apostar mais de 1 vez.";
                                                }
                                    		} else {
                                    			resp = "{nick} - Aposte um valor válido";
                                    		}
                                    	}
                                    } else {
                                    	resp = "{nick} - Aposte um valor válido";
                                    }
                            	}
                            } else if (cmd.split(Config.WHITE_SPACE)[0].equals("!rank")) {
                                if (Config.usuarios.containsKey(nick)) {
                                	Entry<String, Long> usuario = usuarios.stream().filter(a -> a.getKey().equals(nick)).findFirst().get();
                                    int pos = usuarios.indexOf(usuario);
                                    resp = resp.replace("{pos}", String.valueOf(pos + 1)).replace("{mmr}", usuario.getValue() + " de MMR.");
                                    if (jogoDeApostas != null) {
                                        Aposta aposta = jogoDeApostas.getAposta(nick);
                                        if (aposta != null) {
                                            resp += " **Aposta atual: " + (aposta.vitoria ? "!vitoria " : "!derrota ") + aposta.valor;
                                        }
                                    }
                                } else {
                                    resp = "{nick}: Calibrando seu MMR..";
                                }
                            } else if (cmd.equals("!top10")) {
                                StringBuilder sb = new StringBuilder();
                                int i = 0;
                                for (Entry<String, Long> usuario : usuarios.stream().limit(10).collect(Collectors.toList())) {
                                    sb.append("# " + (++i) + "º: " + usuario.getKey() + " com " + usuario.getValue() + "(-" + (11-i) + "%)" + " de MMR ");
                                }
                                resp = resp.replace("{top}", sb.toString());
                            } else if (cmd.equals("!odds")) {
                                printOdds(-1);
                            } else if (cmd.equals("!songname")) {
                            	URLConnection connection = Config.URL_NODECG.openConnection();
                            	BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            	StringBuffer sb = new StringBuffer();
                            	String line;
                            	while ((line = br.readLine()) != null) {
                            		sb.append(line);
                        		}
                            	br.close();
                            	resp = sb.toString();
                            } else if (cmd.equals("!recarregar")) {
                                if (Config.mods.contains(nick)) {
                                    running = false;
                                    Config.loadCommands();
                                    running = true;
                                } else {
                                    continue;
                                }
                            } else if (cmd.equals("!comandos")) {
                                resp = resp.replace("{cmd}", Config.getCommands());
                            }
                        }

                        if (resp != null && !resp.isEmpty()) {
                            resp = resp.replace("{nick}", nick);
                            sendChatMessage(resp);
                        }
                    }
            	} catch (Exception e) {
            		e.printStackTrace();
            	}
            }
		}
	}
	
	private void printOdds(int i) {
        if (jogoDeApostas != null) {
            long total = jogoDeApostas.total();
            if (total > 0) {
                double derrota = jogoDeApostas.totalEmDerrota() * 100.0 / total;
                sendChatMessage("Odds " + (i == -1 ? "" : "(" + (i + 1) + " / " + (Config.REWARD_INTERVAL_BETS) + ")") + " -> Vitoria " + Config.DECIMAL_FORMAT.format(100 - derrota) + "% VS " + Config.DECIMAL_FORMAT.format(derrota) + "% Derrota");
            } else {
                sendChatMessage("Nenhuma aposta até agora BibleThump");
            }
        } else {
            sendChatMessage("Nenhum jogo em andamento SwiftRage");
        }
    }

	public void handleLogin() {
		sendMessage("PASS " + Config.PASS);
		sendMessage("NICK " + Config.NICK);
		sendMessage("USER " + Config.NICK + " " + Config.HOST + " bla " + Config.NICK);

		sendMessage(Config.CAP);

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		sendMessage("JOIN #" + Config.CHANNEL);
		running = true;
	}

	public void sendPMMessage(String nick, String msg) {
		sendMessage("PRIVMSG #" + Config.CHANNEL + " :/w " + nick + " " + msg);
	}

	public void sendChatMessage(String msg) {
		sendMessage("PRIVMSG #" + Config.CHANNEL + " :" + msg);
	}

	public void sendMessage(String msg) {
		try {
			out.write(msg + Config.END_LINE);
			out.flush();
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}