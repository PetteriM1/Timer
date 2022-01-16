import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends ListenerAdapter {

    private static Main instance;
    private static JDA jda;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static String prefix = "!";
    private int taskId;
    private final Map<Integer, ScheduledFuture<?>> tasks = new HashMap<>();
    private final Map<Integer, String> channels = new HashMap<>();

    public Main() {
        instance = this;
    }

    public static void main(String[] args) throws InterruptedException, LoginException {
        System.out.println("Timer 1.0 - Made by PetteriM1");
        if (args.length != 1) {
            System.out.println("Please specify the bot token, run 'java -jar timer.jar <your bot token>'");
            return;
        }
        jda = JDABuilder.createDefault(args[0]).build();
        System.out.println("Waiting JDA to load...");
        jda.awaitReady();
        new Main();
        jda.addEventListener(instance);
        System.out.println("Startup done! Default prefix: " + prefix);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent e) {
        TextChannel channel = e.getChannel();
        String msg = e.getMessage().getContentStripped();
        String msgL = msg.toLowerCase();
        if (e.getMember() != null && msgL.startsWith(prefix)) {
            boolean found = false;
            for (Role role : e.getMember().getRoles()) {
                if (role.getName().equalsIgnoreCase("timer")) {
                    found = true;
                }
            }
            if (!found) {
                channel.sendMessage("Only users with 'timer' role can use the commands").queue();
                return;
            }
            String[] args = msg.split(" ");
            String cmd = args[0].toLowerCase();
            if (cmd.equals(prefix + "start")) {
                if (args.length < 3) {
                    channel.sendMessage("Use <prefix>start <minutes> <message>").queue();
                } else {
                    try {
                        int minutes = Integer.parseInt(args[1]);
                        if (minutes < 1) {
                            channel.sendMessage("Minutes must be a positive number. Use <prefix>start <minutes> <message>").queue();
                        }
                        final int id = ++taskId;
                        StringJoiner sj = new StringJoiner(" ");
                        for (int i = 2; i < args.length; i++) {
                            sj.add(args[i]);
                        }
                        String message = sj.toString().replace("[mention]", "@");
                        channels.put(id, channel.getId());
                        tasks.put(id, scheduler.scheduleAtFixedRate(() -> {
                            TextChannel ch = jda.getTextChannelById(channels.get(id));
                            if (ch != null) {
                                ch.sendMessage(message).queue();
                            } else {
                                ScheduledFuture<?> thisTask = tasks.remove(id);
                                if (thisTask != null) {
                                    thisTask.cancel(false);
                                    channels.remove(id);
                                    System.out.println("Cancelled task " + id + " due to TextChannel not found");
                                }
                            }
                        }, minutes, minutes, TimeUnit.MINUTES));
                        channel.sendMessage("Successfully created a " + minutes + " minute timer. Task id: " + taskId).queue();
                    } catch (NumberFormatException ex) {
                        channel.sendMessage("Minutes must be a positive number. Use <prefix>start <minutes> <message>").queue();
                    }
                }
            } else if (cmd.equals(prefix + "stop")) {
                if (args.length != 2) {
                    channel.sendMessage("Use <prefix>stop <timer id|all>").queue();
                } else if (args[1].equalsIgnoreCase("all")) {
                    AtomicInteger i = new AtomicInteger();
                    tasks.forEach((id, task) -> {
                        task.cancel(false);
                        channels.remove(id);
                        i.getAndIncrement();
                    });
                    tasks.clear();
                    channel.sendMessage(i + (i.get() > 1 ? " tasks cancelled" : " task cancelled")).queue();
                } else {
                    try {
                        int id = Integer.parseInt(args[1]);
                        if (id < 0) {
                            channel.sendMessage("Task id must be a positive number. Use <prefix>stop <timer id|all>").queue();
                        } else {
                            ScheduledFuture<?> task = tasks.remove(id);
                            if (task == null) {
                                channel.sendMessage("No task found with id " + id).queue();
                            } else {
                                task.cancel(false);
                                channels.remove(id);
                                channel.sendMessage("Task cancelled successfully").queue();
                            }
                        }
                    } catch (NumberFormatException ex) {
                        channel.sendMessage("Task id must be a positive number. Use <prefix>stop <timer id|all>").queue();
                    }
                }
            } else if (cmd.equals(prefix + "prefix")) {
                if (args.length != 2) {
                    channel.sendMessage("Use <prefix>prefix <new prefix>").queue();
                } else {
                    prefix = args[1];
                    channel.sendMessage("Prefix set to " + args[1]).queue();
                }
            }
        }
    }
}
