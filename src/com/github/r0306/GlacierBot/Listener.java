package com.github.r0306.GlacierBot;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class Listener extends ListenerAdapter {

	private Connection connection;

	private HashMap<String, String> messageMap = new HashMap<String, String>();
	
	public Listener(Connection connection) {
		this.connection = connection;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getChannel().getId().equals(connection.getId("logChannel")) && event.getAuthor().getId().equals(connection.getId("dynoId"))) {
			for (MessageEmbed embed : event.getMessage().getEmbeds()) {
				if (embed.getDescription().contains(connection.getId("voiceChannel"))) {
					event.getMessage().delete().queue();
					break;
				}
			}
		} else if (!event.getMessage().getContent().isEmpty() && event.getMessage().getContent().charAt(0) == '^') {
			String command = event.getMessage().getContent().substring(1);
			String arguments[] = command.split(" ");
			if (event.getChannel().getId().equals(connection.getId("commandChannel"))) {
				if (arguments[0].equalsIgnoreCase("give")) {
					if (arguments[arguments.length - 1].matches("\\d+") && event.getMessage().getMentionedUsers().size() == 1) {
						if (connection.hasRole(event.getAuthor(), "government")) {
							if (connection.hasRole(event.getMessage().getMentionedUsers().get(0), "glacier citizen")) {
								connection.giveCredits(event.getAuthor(), event.getMessage().getMentionedUsers().get(0), event.getGuild().getTextChannelById(connection.getId("commandChannel")), Integer.parseInt(arguments[arguments.length - 1]));
							}
						} else if (connection.hasRole(event.getAuthor(), "frost")) {
							if (!event.getMessage().getMentionedUsers().get(0).getId().equals(event.getAuthor().getId())) {
								if (connection.hasRole(event.getMessage().getMentionedUsers().get(0), "glacier citizen")) {
									connection.giveCredits(event.getAuthor(), event.getMessage().getMentionedUsers().get(0), event.getGuild().getTextChannelById(connection.getId("commandChannel")), Integer.parseInt(arguments[arguments.length - 1]));
								}
							} else {
								event.getTextChannel().sendMessage(event.getAuthor().getAsMention() + " You can't give yourself credits! :facepalm:").queue();
							}
						} else {
							event.getTextChannel().sendMessage(event.getAuthor().getAsMention() + " You must be rank **Frost** in order to give other people credits!").queue();
						}
					}
				}
			}
			if (event.getChannel().getId().equals(connection.getId("shopChannel"))) {
				if (command.equalsIgnoreCase("createshop")) {
					event.getMessage().delete().queue();
					for (Message message : event.getChannel().getIterableHistory()) {
						message.delete().queue();
					}
					String lastRank = "";
					try {
						for (String[] settings : getEmbeds()) {
							if (!lastRank.equalsIgnoreCase(settings[3])) {
								lastRank = settings[3];
								event.getChannel().sendMessage("**" + lastRank + " Tier Carries**").queue();
								event.getChannel().sendMessage("**Required Contribution: " + connection.getRanks().get(lastRank) + "**").queue();
							}
							event.getChannel().sendMessage(getEmbed(settings)).queue(message -> {message.addReaction("❄").queue(); messageMap.put(message.getId(), message.getEmbeds().get(0).getAuthor().getName());});
						}
					} catch (IOException e) {
						connection.log("ERROR", "Generating the carry shop: " + e.getMessage());
					}
					connection.log("INFO", event.getAuthor().getAsMention() + " has successfully generated the carry shop.");
				}
			} else if (event.getChannel().getId().equals(connection.getId("commandChannel"))) {
				if (command.equalsIgnoreCase("balance")) {
					connection.getCredits(event.getAuthor(), event.getTextChannel());
				}
			}
			else if (event.getChannel().getId().equals(connection.getId("chatChannel"))) {
				if (arguments.length == 2 && arguments[0].equalsIgnoreCase("set")) {
					connection.setId("messageChannel", arguments[1]);
					event.getTextChannel().sendMessage("Channel set!").queue();
				}
			}
		} else if (event.getChannel().getId().equals(connection.getId("chatChannel")) && connection.getId("messageChannel") != null) {
			connection.getJDA().getTextChannelById(connection.getId("messageChannel")).sendMessage(event.getMessage().getContent()).queue();
		}
		else if (event.getChannel().getId().equals(connection.getId("introChannel"))) {
			boolean correct = false;
			String ign = "";
			String[] lines = event.getMessage().getContent().split("\n");
			if (lines.length == 3 && lines[0].startsWith("In-game Name: ")) {
				String[] name = lines[0].split(": ");
				if (name.length == 2 && lines[1].startsWith("Preferred Name: ")) {
					String[] preferred = lines[1].split(": ");
					if (preferred.length == 2 && lines[2].startsWith("Ratify Constitution (Yes/No): ")) {
						String[] ratify = lines[2].split(": ");
						if (ratify.length == 2) {
							if (!ratify[1].equalsIgnoreCase("Yes")) {
								event.getMessage().delete().queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage("You must ratify the constitution in order to join!").queue());
							} else {
								ign = name[1]; 
								correct = true;
							}
						}
					}
				}
			}
			if (!correct) {
				event.getMessage().delete().queue();
				event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage("Incorrect post format! Please read the #welcome channel and follow the instructions on there!").queue());
			} else {
				try {
					connection.backgroundCheck(event.getAuthor(), ign);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		if (event.getChannel().getId().equals(connection.getId("shopChannel"))) {
			if (!event.getUser().isBot()) {
				event.getReaction().removeReaction(event.getUser()).queue();
				event.getChannel().getMessageById(event.getMessageId()).queue(message -> {
					if (message.getEmbeds().size() == 1) {
						MessageEmbed embed = message.getEmbeds().get(0);
						for (MessageEmbed.Field field : embed.getFields()) {
							if (field.getName().equalsIgnoreCase("cost")) {
								String name = embed.getAuthor().getName();
								int cost = Integer.parseInt(field.getValue().split(" ❄ ")[0]);
								connection.postRequest(event.getUser(), name, cost);
								break;
							}
						}
					}
				});
			}
		} else if (event.getChannel().getId().equals(connection.getId("queueChannel"))) {
			if (!event.getUser().isBot()) {
				if (connection.hasRole(event.getUser(), "CIA")) {
					if (event.getReaction().getEmote().getName().equals("✔")) {
						event.getTextChannel().getMessageById(event.getMessageId()).queue(message -> message.delete().queue());
					}
				} else if (connection.hasRole(event.getUser(), "glacier citizen")){
					event.getTextChannel().getMessageById(event.getMessageId()).queue(message -> {
						if (event.getReaction().getEmote().getName().equals("❌") && message.isMentioned(event.getUser())) {
							String[] content = message.getContent().split(" ");
							int credits = Integer.parseInt(content[content.length - 2]);
							connection.refundCredits(event.getUser(), message, credits);
						}
					});
				}
			}
		} else if (event.getChannel().getId().equals(connection.getId("checkInChannel"))) {
			if (!event.getUser().isBot()) {
				if (event.getReaction().getEmote().getName().equals("🕐")) {
					connection.checkIn(event.getUser(), true);
				} else if (event.getReaction().getEmote().getName().equals("🗓")) {
					connection.checkIn(event.getUser(), false);
				}
			}
		}
	}
		
	public List<String[]> getEmbeds() throws IOException {
		List<String[]> embeds = new ArrayList<String[]>();
		LinkedHashMap<String, List<String[]>> sort = new LinkedHashMap<String, List<String[]>>();
		for (String rank : connection.getRanks().keySet()) {
			if (connection.getRanks().get(rank) != null) {
				sort.put(rank, new ArrayList<String[]>());
			}
		}
		int count = 0;
		for (Message message : connection.getJDA().getTextChannelById(connection.getId("shopConfig")).getIterableHistory()) { 
			String[] entries = new String[8];
			String[] lines = message.getContent().split("\n");
			for (String line : lines) {
				if (!line.equals("```")) {
					entries[count++] = line;
				}
				if (count == 8) {
					count = 0;
					sort.get(entries[3]).add(0, entries);
				}
			}
		}
		for (String rank : sort.keySet()) {
			List<String[]> list = sort.get(rank);
			for (String[] embed : list) { 
				embeds.add(embed);
			}
		}
		return embeds;
	}
	
	public MessageEmbed getEmbed(String[] list) {
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle(null);
		builder.setAuthor(list[0], list[1]);
		builder.setThumbnail(list[2]);
		Color color = new Color(0, 0, 0);
		if (list[3].equalsIgnoreCase("droplet")) {
			color = new Color(0, 0, 255);
		} else if (list[3].equalsIgnoreCase("snowflake")) {
			color = new Color(0, 255, 0);
		} else if (list[3].equalsIgnoreCase("frost")) {
			color = new Color(255, 255, 0);
		} else if (list[3].equalsIgnoreCase("frost+")) {
			color = new Color(255, 0, 0);
		}
		builder.setColor(color);
		builder.addField("Required Level", list[4], true);
		builder.addField("Cost", list[5] + " ❄ credits", true);
		builder.addField("Resets", list[6], false);
		builder.addField("Drops", list[7], false);
		return builder.build();
	}
	
}
