package ee.mpagel;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PoeBot {
    private static final Map<String, Command> commands = new HashMap<>();
    private static final Map<String, List<String>> itemMap = new HashMap<>();

    public static void main(String[] args) {
        loadCommands();
        final DiscordClient client = new DiscordClientBuilder("").build();
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.justOrEmpty(event.getMessage().getContent())
                        .flatMap(content -> Flux.fromIterable(commands.entrySet())
                                // We will be using ! as our "prefix" to any command in the system.
                                .filter(entry -> content.startsWith('!' + entry.getKey()))
                                .flatMap(entry -> entry.getValue().execute(event))
                                .next()))
                .subscribe();

        client.login().block();

    }

    private static void loadCommands() {
        commands.put("need", event -> {

            event.getMember().ifPresent(member -> {
                event.getMessage().getContent().ifPresent(s -> {
                    if (s.length() < 6) {
                        return;
                    }
                    itemMap.compute(member.getUsername(), (s1, strings) -> {
                        if (strings == null) {
                            strings = new ArrayList<>();
                            strings.add(s.substring(6).toLowerCase());
                        } else {
                            strings.add(s.substring(6).toLowerCase());
                        }
                        return strings;
                    });
                });
            });
            return Mono.empty();
        });

        commands.put("drop", event -> {
            List<String> wants = new ArrayList<>();
            event.getMessage().getContent().ifPresent(s -> {
                if (s.length() < 6) {
                    return;
                }
                String drop = s.substring(6).toLowerCase();
                for (Map.Entry<String, List<String>> member : itemMap.entrySet()) {
                    for (String item : member.getValue()) {
                        if (item.equals(drop)) {
                            wants.add(member.getKey());
                            break;
                        }
                    }
                }

            });
            return Mono.just(wants).zipWith(event.getMessage().getChannel()).flatMap(tuple
                    -> tuple.getT2().createMessage(wants.toString()).then()).then();
        });

        commands.put("showAll", event -> event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage(itemMap.toString()))
                .then());

        commands.put("noNeed", event -> {

            event.getMember().ifPresent(member -> {
                event.getMessage().getContent().ifPresent(s -> {
                    if (s.length() < 8) {
                        return;
                    }
                    List<String> items = itemMap.get(member.getUsername());
                    if (items != null) {
                        items.removeIf(s1 -> s1.equalsIgnoreCase(s.substring(8)));
                    }
                });
            });
            return Mono.empty();
        });

        commands.put("clear", event -> {

            event.getMember().ifPresent(member -> {
                List<String> items = itemMap.get(member.getUsername());
                if (items != null) {
                    items.clear();
                }
            });
            return Mono.empty();
        });

    }
}
