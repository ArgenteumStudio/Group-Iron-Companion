package net.runelite.client.plugins.bowlsofmayo;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import com.google.inject.Provides;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@PluginDescriptor(
	name = "mayoclan"
)
@Slf4j
public class ClanTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClanTrackerConfig config;

	@Inject
	private ClientThread clientThread;

	private HttpServer server;
	private final Gson gson = new Gson();

	@Provides
	ClanTrackerConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanTrackerConfig.class);
	}

	@Override
	protected void startUp()
		throws Exception
	{
		startServer();
	}

	@Override
	protected void shutDown()
		throws Exception
	{
		stopServer();
	}

	private void startServer()
	{
		stopServer();

		try
		{
			int port = config.statsPort();
			server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext("/stats", new StatsHandler());
			server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
			server.start();
			log.info("BowlsofMayo stats HTTP server started on port {}", port);
		}
		catch (IOException e)
		{
			log.warn("Failed to start stats HTTP server", e);
		}
	}

	private void stopServer()
	{
		if (server != null)
		{
			server.stop(0);
			server = null;
		}
	}

	private class StatsHandler implements HttpHandler
	{
		@Override
		public void handle(HttpExchange exchange)
			throws IOException
		{
			try
			{
				String query = exchange.getRequestURI().getQuery();
				java.util.Map<String, String> params = parseQuery(query);
				boolean wantImage = "true".equalsIgnoreCase(params.getOrDefault("image", "false"));
				boolean mock = "true".equalsIgnoreCase(params.getOrDefault("mock", "false"));

				// Gather stats on client thread to avoid race with client internals
				java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();

				clientThread.invoke(() ->
				{
					for (Skill s : Skill.values())
					{
						try
						{
							int lvl = client.getBoostedSkillLevel(s);
							long xp = client.getSkillExperience(s);
							java.util.Map<String, Object> m = new java.util.HashMap<>();
							m.put("level", lvl);
							m.put("xp", xp);
							payload.put(s.name().toLowerCase(), m);
						}
						catch (Exception ex)
						{
							// ignore skills we can't read
						}
					}
				});

				// If no data (not logged in) and mock requested, fill with sample data
				if (payload.isEmpty() && mock)
				{
					int[][] mockStats = {
						{99, 200000000}, {99, 13034431}, {99, 13034431}, {99, 13034431},
						{99, 13034431}, {99, 13034431}, {99, 13034431}, {99, 13034431},
						{99, 13034431}, {99, 13034431}, {99, 13034431}, {99, 13034431},
						{99, 13034431}, {99, 13034431}, {99, 13034431}, {99, 13034431},
						{99, 13034431}, {99, 13034431}, {99, 13034431}, {99, 13034431},
						{99, 13034431}, {99, 13034431}, {99, 13034431}
					};
					Skill[] skills = Skill.values();
					for (int i = 0; i < skills.length && i < mockStats.length; i++)
					{
						java.util.Map<String, Object> m = new java.util.HashMap<>();
						m.put("level", mockStats[i][0]);
						m.put("xp", mockStats[i][1]);
						payload.put(skills[i].name().toLowerCase(), m);
					}
				}

				if (wantImage && config.enableImage())
				{
					byte[] png = renderImage(payload);
					exchange.getResponseHeaders().add("Content-Type", "image/png");
					exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
					exchange.sendResponseHeaders(200, png.length);
					try (OutputStream os = exchange.getResponseBody())
					{
						os.write(png);
					}
				}
				else
				{
					String body = gson.toJson(payload);
					byte[] out = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
					exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
					exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
					exchange.sendResponseHeaders(200, out.length);
					try (OutputStream os = exchange.getResponseBody())
					{
						os.write(out);
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Error handling /stats", e);
				String msg = "{}";
				byte[] out = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
				exchange.sendResponseHeaders(500, out.length);
				try (OutputStream os = exchange.getResponseBody())
				{
					os.write(out);
				}
			}
		}

		private java.util.Map<String, String> parseQuery(String q)
		{
			java.util.Map<String, String> m = new java.util.HashMap<>();
			if (q == null || q.isEmpty())
			{
				return m;
			}

			for (String part : q.split("&"))
			{
				int idx = part.indexOf('=');
				if (idx > 0)
				{
					String k = urlDecode(part.substring(0, idx));
					String v = urlDecode(part.substring(idx + 1));
					m.put(k, v);
				}
				else
				{
					m.put(urlDecode(part), "");
				}
			}

			return m;
		}

		private String urlDecode(String s)
		{
			try
			{
				return java.net.URLDecoder.decode(s, "UTF-8");
			}
			catch (Exception e)
			{
				return s;
			}
		}

		private byte[] renderImage(java.util.Map<String, Object> payload)
		{
			int rows = Math.max(1, payload.size());
			int w = 220;
			int h = rows * 18 + 20;
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			try
			{
				g.setColor(new Color(30, 30, 30));
				g.fillRect(0, 0, w, h);
				g.setColor(Color.WHITE);
				g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

				int y = 16;
				for (java.util.Map.Entry<String, Object> e : payload.entrySet())
				{
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> m = (java.util.Map<String, Object>) e.getValue();
					String line = String.format("%s: %s", e.getKey(), m.getOrDefault("level", "-"));
					g.drawString(line, 6, y);
					y += 18;
				}
			}
			finally
			{
				g.dispose();
			}

			try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
			{
				ImageIO.write(img, "png", baos);
				return baos.toByteArray();
			}
			catch (IOException ex)
			{
				return new byte[0];
			}
		}
	}
}
