package com.github.r0306.GlacierBot;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.security.auth.login.LoginException;

import org.json.JSONObject;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.RateLimitedException;

public class Connection {
	
	private JDA jda;
	private DatabaseReference db;
	private DatabaseReference checkIn;
	
	private HashMap<String, String> ids = new HashMap<String, String>();
	private LinkedHashMap<String, String> ranks = new LinkedHashMap<String, String>();
	private LinkedHashMap<String, Integer> credits = new LinkedHashMap<String, Integer>();

	public Connection(String token) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException, IOException, SchedulerException {
		jda = new JDABuilder(AccountType.BOT).setToken(token).buildBlocking();
		jda.addEventListener(new Listener(this));
		FileInputStream serviceAccount = new FileInputStream("config.json");
		FirebaseOptions options = new FirebaseOptions.Builder()
				.setCredentials(GoogleCredentials.fromStream(serviceAccount))
			    .setDatabaseUrl("https://glacierbot-d0084.firebaseio.com/")
			    .build();
		FirebaseApp.initializeApp(options);
		db = FirebaseDatabase.getInstance().getReference("users");
		checkIn = FirebaseDatabase.getInstance().getReference("checkin");
		ids.put("idConfig", "441716353217331211");
		ids.put("logConfig", "441709022068867072");
		for (Message message : jda.getTextChannelById(getId("idConfig")).getIterableHistory()) {
			String[] lines = message.getContent().split("\n");
			for (String line : lines) {
				String[] content = line.split(" - ");
				if (content.length == 3) {
					ids.put(content[0], content[1]);
				}
			}
		}
		for (Message message : jda.getTextChannelById(getId("tierConfig")).getIterableHistory()) {
			String[] lines = message.getContent().split("\n");
			for (String line : lines) {
				String[] content = line.split(" - ");
				if (content.length == 3) {
					ranks.put(content[0], content[1]);
					credits.put(content[0], Integer.parseInt(content[2]));
				} else {
					ranks.put(content[0], null);
				}
			}
		}
		log("INFO", "Bot has started.");
		setCheckIn();
		for (Member member : jda.getGuildById(getId("serverId")).getMembers()) {
			if (!member.getUser().isBot() && hasRole(member, "glacier citizen")) {
				db.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
		            public void onDataChange(DataSnapshot snapshot) {
		                if (!snapshot.hasChild(member.getUser().getId())) {
		                    db.child(member.getUser().getId()).child("snowflakes").setValueAsync(5);
		                }
		            }
		            @Override
		            public void onCancelled(DatabaseError databaseError) {
		            }
				});		
			}
		}
	}
	
	public void backgroundCheck(User user, String name) throws IOException {
		URLConnection connection = new URL("http://www.pinkbean.xyz/api/rank/na/" + name + "/overall").openConnection();
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		connection.connect();
		try {
			InputStream in = connection.getInputStream();
			BufferedReader streamReader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			StringBuilder responseStrBuilder = new StringBuilder(2048);
			String inputStr;
			while ((inputStr = streamReader.readLine()) != null) {
				responseStrBuilder.append(inputStr);
			}
			JSONObject object = new JSONObject(responseStrBuilder.toString());
			if (object.has("name")) {
				EmbedBuilder builder = new EmbedBuilder();
				builder.setTitle(null);
				builder.setAuthor(name, "http://maplestory.nexon.net/rankings/overall-ranking/monthly?pageIndex=1&character_name=" + name + "&search=true");
				builder.setThumbnail(object.getString("avatar"));
				builder.setColor(new Color(0, 200, 255));
				builder.addField("Job", object.getString("job"), false);
				builder.addField("Level", String.valueOf(object.getInt("level")), false);
				builder.addField("Legion", object.getString("legion"), false);
				builder.addField("Rank Movement", object.getString("rankDirection") + " " + object.getInt("rankMovement"), false);
				jda.getTextChannelById(getId("backgroundCheckChannel")).sendMessage(builder.build()).queue();
			}
		} catch (FileNotFoundException e) {
			String mention = jda.getGuildById(getId("serverId")).getRolesByName("government", true).get(0).getAsMention();
			jda.getTextChannelById(getId("backgroundCheckChannel")).sendMessage(mention + " [**POTENTIAL GOON: ** " + user.getName() + "] Character name: " + name + " not on ranks!").queue();
		}
	}
	
	public MessageEmbed getEmbed() {
		Date date = new Date();
		LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		String name = localDate.getDayOfWeek().toString();
		name = name.substring(0, 1) + name.substring(1).toLowerCase();
		String month = localDate.getMonth().toString();
		month = month.substring(0, 1) + month.substring(1).toLowerCase();
		int day = localDate.getDayOfMonth();
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle("Check In");
		builder.setDescription("**" + name.toString() + ", " + month + " " + day + "**");
		Color color = new Color(0, 200, 255);
		builder.setColor(color);
		builder.addField("Daily", "Click the :clock1: icon for daily check-in. Resets at the end of every day 11:59 PM PST.", false);
		builder.addField("Weekly", ":warning: **DON'T DO THIS IF YOU ARE PLANNING ON CHECKING IN DAILY!!** :warning:\nClick the :calendar_spiral: for weekly check-in at a reduced credit rate. Resets and handed out every Sunday at 11:59 PST.", false);
		builder.addField("More Information", "Read the #carry-info channel for more details.", false);
		return builder.build();
	}
	
	public void setCheckIn() throws SchedulerException {
		for (Message message : jda.getTextChannelById(getId("checkInChannel")).getIterableHistory()) {
			message.delete().queue();
		}
		jda.getTextChannelById(getId("checkInChannel")).sendMessage(getEmbed()).queue(message -> {message.addReaction("🕐").queue(); message.addReaction("🗓").queue();});
		Trigger dailyTrigger = TriggerBuilder.newTrigger().withIdentity("dailyResetTrigger", "reset").withSchedule(CronScheduleBuilder.cronSchedule("59 59 23 * * ?")).build();
		JobDetail dailyReset = JobBuilder.newJob(ScheduledDailyReset.class).withIdentity("dailyResetJob", "reset").build();
		Trigger weeklyTrigger = TriggerBuilder.newTrigger().withIdentity("weeklyResetTrigger", "reset").withSchedule(CronScheduleBuilder.cronSchedule("59 59 23 ? * 1")).build();
		JobDetail weeklyReset = JobBuilder.newJob(ScheduledDailyReset.class).withIdentity("weeklyResetJob", "reset").build();
		Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
		scheduler.start();
		scheduler.scheduleJob(dailyReset, dailyTrigger);
		scheduler.scheduleJob(weeklyReset, weeklyTrigger);
	}
	
	public void resetCheckInDaily() {
		checkIn.child("daily").removeValueAsync();
		jda.getTextChannelById(getId("checkInChannel")).getIterableHistory().getFirst().editMessage(getEmbed()).queue();
		jda.getTextChannelById(getId("checkInChannel")).getIterableHistory().getFirst().getReactions().forEach(reaction -> {
			if (reaction.getEmote().getName().equals("🕐")) {
				reaction.getUsers().forEach(user -> {if (!user.isBot()) reaction.removeReaction(user).queue();});
			}
		});
	}
	
	public void resetCheckInWeekly() {
		checkIn.child("weekly").removeValueAsync();
		jda.getTextChannelById(getId("checkInChannel")).getIterableHistory().getFirst().editMessage(getEmbed()).queue();
		jda.getTextChannelById(getId("checkInChannel")).getIterableHistory().getFirst().getReactions().forEach(reaction -> {
			if (reaction.getEmote().getName().equals("🗓")) {
				reaction.getUsers().forEach(user -> {if (!user.isBot()) reaction.removeReaction(user).queue();});
			}
		});	
	}
	
	public void checkIn(User user, boolean daily) {
		checkIn.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot snapshot) {
				@SuppressWarnings("unchecked")
				List<String> dailyList = (List<String>) snapshot.child("daily").getValue();
				if (dailyList == null) {
					dailyList = new ArrayList<String>();
				}
				@SuppressWarnings("unchecked")
				HashMap<String, Long> weeklyList = (HashMap<String, Long>) snapshot.child("weekly").getValue();
				if (weeklyList == null) {
					weeklyList = new HashMap<String, Long>();
				}
				if (daily) {
					if (!weeklyList.containsKey(user.getId()) && !dailyList.contains(user.getId())) {
						String role = getRole(user);
						if (role != null) {
							int credit = credits.get(role);
							dailyList.add(user.getId());
							if (weeklyList.containsKey(user.getId())) {
								weeklyList.put(user.getId(), weeklyList.get(user.getId()) + 1);
							} else {
								weeklyList.put(user.getId(), (long) 1);	
							}
							checkIn.child("daily").setValueAsync(dailyList);
							db.addListenerForSingleValueEvent(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot snapshot) {
									if (snapshot.hasChild(user.getId())) {
										long current = (long) snapshot.child(user.getId()).child("snowflakes").getValue();	
										db.child(user.getId()).child("snowflakes").setValueAsync(current + credit);
									}
								}
								@Override
								public void onCancelled(DatabaseError error) {
								}
							});
							jda.getTextChannelById(getId("commandChannel")).sendMessage(user.getAsMention() + " You have earned **" + credit + " ❄ credits** for checking in today.").queue();
						}
					}
				} else {
					long times = 7;
					if (weeklyList.containsKey(user.getId())) {
						times = (long) 7 - weeklyList.get(user.getId());
					}
					String role = getRole(user);
					if (role != null) {
						long credit = times * (credits.get(role) / 2);
						if (credit != 0) {
							weeklyList.put(user.getId(), (long) 7);
							checkIn.child("weekly").setValueAsync(weeklyList);
							db.addListenerForSingleValueEvent(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot snapshot) {
									if (snapshot.hasChild(user.getId())) {
										long current = (long) snapshot.child(user.getId()).child("snowflakes").getValue();	
										db.child(user.getId()).child("snowflakes").setValueAsync(current + credit);
									}
								}
								@Override
								public void onCancelled(DatabaseError error) {
								}
							});
							jda.getTextChannelById(getId("commandChannel")).sendMessage(user.getAsMention() + " You have earned **" + credit + " ❄ credits** for checking in for this week.").queue();
						}
					}
				}
			}
			@Override
			public void onCancelled(DatabaseError error) {
			}
		});
	}
	
	public void giveCredits(User sender, User receiver, TextChannel channel, int amount) {
		db.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot snapshot) {
				if (snapshot.hasChild(receiver.getId())) {
					long current = (long) snapshot.child(receiver.getId()).child("snowflakes").getValue();
					if (hasRole(sender, "government")) {
						if (snapshot.hasChild(sender.getId())) {
							long balance = (long) snapshot.child(sender.getId()).child("snowflakes").getValue();
							if (balance >= amount) {
								db.child(receiver.getId()).child("snowflakes").setValueAsync(amount + current);
								channel.sendMessage(sender.getAsMention() + " has given " + receiver.getAsMention() + " ** " + amount + "❄ credits**.").queue();									
								
							} else {
								channel.sendMessage(sender.getAsMention() + " You do not have enough credits!").queue();
							}
						}
					} else {
						db.child(receiver.getId()).child("snowflakes").setValueAsync(amount + current);
						channel.sendMessage(sender.getAsMention() + " has given " + receiver.getAsMention() + " ** " + amount + "❄ credits**.").queue();									
					}					
				}
			}
			@Override
			public void onCancelled(DatabaseError error) {
			}
		});
	}
	
	public void postRequest(User user, String name, int cost) {
		db.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot snapshot) {
				if (snapshot.hasChild(user.getId())) {
					long balance = (long) snapshot.child(user.getId()).child("snowflakes").getValue();
					if (balance >= cost) {
						jda.getTextChannelById(getId("queueChannel")).sendMessage(user.getAsMention() + " requested a carry for **" + name + "** - " + cost + " credits.").queue(message -> {message.addReaction("✔").queue(); message.addReaction("❌").queue();});
						db.child(user.getId()).child("snowflakes").setValueAsync(balance - cost);
						jda.getTextChannelById(getId("commandChannel")).sendMessage(user.getAsMention() + " Your new balance is **" + (balance - cost) + " ❄ credits**.").queue();
					} else {
						jda.getTextChannelById(getId("commandChannel")).sendMessage(user.getAsMention() + " You do not have enough credits for **" + name + "**!").queue();
					}
				}
			}
			@Override
			public void onCancelled(DatabaseError error) {
			}
		});
	}
	
	public void refundCredits(User user, Message message, int credits) {
		db.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot snapshot) {
				if (snapshot.hasChild(user.getId())) {
					long balance = (long) snapshot.child(user.getId()).child("snowflakes").getValue();
					db.child(user.getId()).child("snowflakes").setValueAsync(balance + credits);
					message.delete().queue();
					jda.getTextChannelById(getId("commandChannel")).sendMessage(user.getAsMention() + " Your carry request has been cancelled and your credits have been refunded.").queue();
				}
			}
			@Override
			public void onCancelled(DatabaseError error) {
			}
		});
	}
	
	public void getCredits(User user, TextChannel channel) {
		if (hasRole(user, "glacier citizen")) {
			db.addListenerForSingleValueEvent(new ValueEventListener() {
				@Override
				public void onDataChange(DataSnapshot snapshot) {
					if (snapshot.hasChild(user.getId())) {
						channel.sendMessage(user.getAsMention() + " You have **" + String.valueOf(snapshot.child(user.getId()).child("snowflakes").getValue()) + " ❄ credits.**").queue();
					} else {
	                    db.child(user.getId()).child("snowflakes").setValueAsync(5);
						channel.sendMessage(user.getAsMention() + " You have **5 ❄ credits.**").queue();
					}
				}
				@Override
				public void onCancelled(DatabaseError error) {
				}
			});
			return;
		}
		channel.sendMessage(user.getAsMention() + " You are not a Glacier Citizen!").queue();
	}
	
	public String getId(String name) {
		if (ids.containsKey(name)) {
			return ids.get(name);
		}
		return null;
	}
	
	public void setId(String name, String id) {
		ids.put(name, id);
	}
	
	public String getRole(User user) {
		String role = null;
		for (String rank : ranks.keySet()) {
			if (hasRole(user, rank)) {
				role = rank;
			}
		}
		return role;
	}
	
	public boolean hasRole(User user, String role) {
		Member member = jda.getGuildById(getId("serverId")).getMember(user);
		return hasRole(member, role);
	}
	
	public boolean hasRole(Member member, String role) {
		if (member != null) {
			for (Role rank : member.getRoles()) {
				if (rank.getName().equalsIgnoreCase(role)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public HashMap<String, String> getRanks() {
		return ranks;
	}
	
	public void log(String tag, String message) {
		jda.getTextChannelById(getId("logConfig")).sendMessage("**[" + tag + "] ** " + message).queue();
	}
	
	public JDA getJDA() {
		return jda;
	}
	
}
